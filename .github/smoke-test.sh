#!/usr/bin/env bash
# An unknown code is answered, the actuator is not on the public port, and readiness is UP.
set -euo pipefail

kubectl --namespace shortly port-forward deploy/redirect-service 18080:8080 18081:8081 &
forward=$!
trap 'kill $forward' EXIT
for _ in $(seq 1 30); do
  curl --silent --output /dev/null http://localhost:18081/actuator/health && break
  sleep 1
done

status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:18080/000000000)
[[ "$status" == 404 ]] || { echo "an unknown code answered $status, expected 404"; exit 1; }
status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:18080/actuator/health)
[[ "$status" == 404 ]] || { echo "the actuator answered $status on the public port"; exit 1; }
curl --fail --silent --show-error http://localhost:18081/actuator/health/readiness | grep -q '"status":"UP"'
