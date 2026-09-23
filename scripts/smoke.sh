#!/bin/sh
set -eu
base="${1:-http://localhost:18080}"
created=$(curl --fail --silent --show-error "$base/api/v1/simulations" -H 'Content-Type: application/json' -d '{"players":[{"name":"AA","cards":["AS","AH"]},{"name":"KK","cards":["KS","KH"]},{"name":"QQ","cards":["QS","QH"]}],"board":[],"iterations":100000,"batchSize":10000,"seed":42}')
path=$(printf '%s' "$created" | jq -r .statusUrl)
for attempt in $(seq 1 150); do
  status=$(curl --fail --silent --show-error "$base$path")
  state=$(printf '%s' "$status" | jq -r .status)
  if [ "$state" = COMPLETED ]; then
    curl --fail --silent --show-error "$base$path/results" | jq -e '.totalTrials == 100000 and ([.players[].equity] | add | . > 0.99999999 and . < 1.00000001)'
    exit 0
  fi
  case "$state" in FAILED|CANCELLED) printf '%s\n' "$status"; exit 1;; esac
  sleep 2
done
echo 'Simulation timed out' >&2
exit 1
