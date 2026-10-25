# redirect-service

The public half of Shortly: `GET https://short.ly/{code}` → `302` to the target. Read-only,
stateless, built so that a traffic spike on one link reaches the database at most once per
instance.

## How a redirect is answered

1. **Shape check.** A code is exactly nine base62 characters (`RedirectService`). Anything else is
   a `404` page without touching the cache or the database.
2. **Cache.** `LinkCacheService` (Caffeine, up to 500,000 entries) holds one `LinkSnapshot` per code with the
   link's **version**. A hit answers from memory. Concurrent misses on the same code share one
   load (`asMap().compute`), so a brand-new viral link costs one replica read per instance.
3. **Miss.** `LinkRepository` reads the `link_snapshot` view on a read replica behind a
   bulkhead (5 concurrent, 10 ms wait) and a circuit breaker. If the replica cannot be asked and
   nothing is cached: `503 Retry-After: 1`. If something is cached: the cached state is served
   and retried in a second.
4. **Decision.** `ACTIVE` → `302` with `Cache-Control: private, no-store` and
   `Referrer-Policy: no-referrer`; `INACTIVE` → `410` page; `DELETED`, unknown code, or a status
   this build does not recognise → `404` page. `HEAD` is answered but never counted.
5. **Click.** `SERVED` and `BLOCKED` are published to the Kafka topic `clicks`, keyed by code
   (`ClickService` builds them, `producer/ClickProducer` sends them), as the JSON of
   `model/ClickEvent`. No IP, no user
   agent, no full referrer URL: only the referrer's host and the edge's country header. The
   redirect never waits on Kafka: one background sender takes the clicks from a bounded queue
   (100,000) and hands them to the Kafka client. While Kafka is away the client holds them in its
   own buffer (32 MiB, the client's default) and drops any click not delivered within 10 minutes.
   Only once that buffer is full does the sender wait, and clicks then pile up in the queue
   (`shortly.clicks.queued`); a click that finds the queue full is dropped at once. Both drops
   count in `shortly.clicks.dropped`. The topic is this
   service's output but infrastructure creates it: 6 partitions (the most dashboard pods that can
   share the counting; raise it in infrastructure to scale, it can never go down) and 7 days'
   retention. Locally, compose's `kafka-init` creates it.

## How a deactivation reaches every instance

The dashboard stamps every insert and every change of a link with the next `change_seq` and with
`updated_at`, the writing transaction's start time; its writers run with `transaction_timeout`
2 s. Every redirect instance polls `link_snapshot WHERE change_seq > :last ORDER BY change_seq` on
the **primary** every 500 ms (`FeedPoller`). A change number is taken at update time but becomes
visible at commit, so each poll also re-reads the rows with `updated_at` newer than the previous
poll's start minus an **overlap window of 5 s** (`shortly.redirect.feed.overlap-window`: the
2 s cap plus 3 s for the pod's clock differing from the database's), and applies each row to the cache **by
version**: a stale replica read racing a deactivation can never overwrite it
(`olderLoadNeverOverwritesNewerFeedState`). While the feed is unhealthy (no successful poll for 2 s) the cache falls back to a 1 s TTL. Links are
never hard-deleted, so nothing is pruned and there is no gap to recover from. An instance is
*ready* only after its first successful poll.

## Contract with dashboard-service

No shared code. The redirect depends on one file it holds a verbatim copy of:

- `contract/link-contract.sql` — the `link_snapshot` view. Tests here run
  against exactly that SQL (`ContractDatabase`); the dashboard proves its migrated schema
  matches it.

The click event is the JSON of `model/ClickEvent`; the dashboard holds the same record, with the
rules a click must meet to be counted.

## Run

```bash
./gradlew test              # unit tests, no Docker
./gradlew integrationTest   # integration tests (Docker/Testcontainers)
./gradlew check             # both suites plus SpotBugs
./gradlew build             # check + assemble, JaCoCo report
./gradlew spotlessApply # google-java-format (AOSP); `spotlessCheck` runs in CI
./gradlew bootRun       # expects Postgres with the contract objects on localhost:5432
docker build -t shortly/redirect-service:dev .
```

Configuration lives in `application.yml` under `shortly.redirect.*` and `spring.datasource.*` (`CacheProperties`, `FeedProperties` and `ClickProperties`
document every knob, except `feed.poll-interval` (500 ms), which `@Scheduled` reads directly). Database login: `redirect_reader`, which infrastructure makes a member of the dashboard's `link_reader` role (SELECT on `link_snapshot` only), with `CONNECTION LIMIT 64` (the largest fleet plus pods still shutting down). Postgres counts it per server: at 8 pods (6 plus a 25 % rollout surge) the reader sees 40 connections and the writer 8. Locally both pools share one server: 48. The local role comes from `local/database-roles.sql`. Environment variables (all optional
locally): `DATABASE_REPLICA_HOST` and `DATABASE_PRIMARY_HOST` (default `localhost`), shared
`DATABASE_PORT` (`5432`), `DATABASE_NAME` (`shortly`) and `DATABASE_CONN_PARAMS` (empty; the chart sets
`?sslmode=verify-full&sslrootcert=/etc/ssl/rds/global-bundle.pem`, a bundle the image carries), `DATABASE_USER` / `DATABASE_PASSWORD` (`redirect_reader`), `SERVER_PORT` (8080),
`KAFKA_BOOTSTRAP_SERVERS` (`localhost:9092`), `KAFKA_SECURITY_PROTOCOL` / `KAFKA_SASL_MECHANISM` /
`KAFKA_SASL_JAAS_CONFIG` (`PLAINTEXT`, `PLAIN`, empty; production uses SASL/SCRAM).

The image builds the jar itself (no local JDK needed), runs as a non-root user and bundles a
pinned `dd-java-agent` (SHA-256 verified), started through `JAVA_OPTS`/`ENTRYPOINT`, and logs JSON
(`LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`). Pass `--build-arg DD_GIT_REPOSITORY_URL=… --build-arg DD_GIT_COMMIT_SHA=…` to link
traces to the source.

## Deploy

Both workflows call `infra`'s shared ones, pinned to an `infra` release (`@v1.0.0`).

Publishing a GitHub Release (tag `vX.Y.Z`) deploys to the one environment, production, through
`deploy.yaml` → `infra`'s `deploy-service.yml`: validate the tag → build → Trivy → push to ECR →
SBOM + cosign → `helm upgrade --install --atomic --wait --timeout 10m` of `infra`'s published
`shortly-service` chart (`chart-version`) with `charts/values.production.yaml` → this service's
`.github/smoke-test.sh`, rolled back if it fails. Local runs use `infra/local/docker-compose.yml`.

On a fresh environment, release dashboard-service first: this service becomes ready only after
reading `link_snapshot`, which the dashboard's migrations create.

`code_quality.yaml` → `infra`'s `code-quality.yml` runs on every PR, on main and nightly:
gitleaks, `spotlessCheck build` (tests + JaCoCo). AWS access is OIDC
(`role-to-assume`), no static keys.
Repository variables: `AWS_REGION`, `ECR_REGISTRY`, `AWS_ECR_PUSH_ROLE_ARN`. GitHub environment `production`: `AWS_DEPLOY_ROLE_ARN`, `EKS_CLUSTER_NAME`, and
required reviewers.

## Operate

- Actuator runs on the management port 8081, never on the public 8080. Metrics at
  `/actuator/prometheus`: `shortly.cache.size`,
  `shortly.feed.age_seconds`, `shortly.clicks.queued`, `shortly.clicks.dropped`, `resilience4j.*`
  for the replica guard.
- Readiness `/actuator/health/readiness` includes the `feed` indicator; liveness is the JVM.
- Production values for the shared `shortly-service` chart (in `infra`) in
  `charts/values.production.yaml`: own node group, HPA 2–6 on CPU (60 %), PDB, rolling update
  25 % / 0, public ALB at `origin.shortly.internal` behind CloudFront, network policy (load balancer + Postgres + Kafka + Datadog agent
  + DNS only); what AWS and the cluster provide is in `infra`'s `AWS.md`. Check them without a
  cluster: `helm template redirect-service ../infra/charts/shortly-service --values charts/values.production.yaml --set image.repository=x --set image.digest=sha256:0`.
- Secrets: `externalSecrets` renders one `ExternalSecret` per entry
  (`shortly/production/redirect-service/{database,kafka}` in AWS Secrets Manager: `username`,
  `password`; `bootstrap_servers`, `jaas_config`) into Secrets loaded with `envFrom`;
  Reloader restarts the pods when it changes.
- Datadog: `tags.datadoghq.com/{env,service,version}` labels (version = release tag) and
  `DD_ENV/DD_SERVICE/DD_VERSION/DD_AGENT_HOST`; metrics through an autodiscovery OpenMetrics check
  on `/actuator/prometheus` (namespace `shortly`).
- Each pool sets its own `statement_timeout` per connection: 25 ms on the replica, sized for one-row cache misses, and 2 s on the primary (feed) pool, for batches of up to 10,000 rows.
