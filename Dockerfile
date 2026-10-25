# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM scratch AS dd-agent
ADD --chmod=644 \
    --checksum=sha256:5f0eb51160fade367d97404624561b6666f7475fb1453a7a73237eb643e398d8 \
    https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/1.66.0/dd-java-agent-1.66.0.jar \
    /datadog/dd-java-agent.jar

FROM scratch AS rds-ca
ADD --chmod=644 \
    --checksum=sha256:90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
    /certs/global-bundle.pem

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --create-home shortly
USER shortly
WORKDIR /app
COPY --from=build /src/build/libs/app.jar app.jar
COPY --from=dd-agent /datadog/dd-java-agent.jar /datadog/dd-java-agent.jar
COPY --from=rds-ca /certs/global-bundle.pem /etc/ssl/rds/global-bundle.pem

ARG DD_GIT_REPOSITORY_URL
ARG DD_GIT_COMMIT_SHA
ENV DD_GIT_REPOSITORY_URL=${DD_GIT_REPOSITORY_URL} \
    DD_GIT_COMMIT_SHA=${DD_GIT_COMMIT_SHA}

ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC" \
    DD_LOGS_INJECTION=true \
    DD_TRACE_SAMPLE_RATE=1 \
    DD_PROFILING_ENABLED=true \
    DD_DBM_PROPAGATION_MODE=full \
    DD_DATA_STREAMS_ENABLED=true
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "exec java -javaagent:/datadog/dd-java-agent.jar -Duser.timezone=UTC $JAVA_OPTS -jar /app/app.jar"]
