#!/bin/bash
set -euo pipefail

CONTAINER=${1:-notification-rabbitmq}
QUEUE=${2:-notification.work}
INTERVAL=${3:-5}

START=$(date +%s)
echo "=========================================="
echo " Queue Depth 모니터링 시작: $(date '+%H:%M:%S')"
echo " Queue   : $QUEUE"
echo " 간격    : ${INTERVAL}초"
echo "=========================================="

while true; do
  STATS=$(docker exec "$CONTAINER" rabbitmqctl list_queues -q name messages_ready messages_unacknowledged 2>/dev/null \
    | awk -v queue="$QUEUE" '$1 == queue { print $2 " " $3 }')

  if [ -z "$STATS" ]; then
    READY="N/A"
    UNACK="N/A"
    TOTAL="N/A"
  else
    read -r READY UNACK <<< "$STATS"
    TOTAL=$((READY + UNACK))
  fi

  ELAPSED=$(( $(date +%s) - START ))
  echo "$(date '+%H:%M:%S') | 경과 ${ELAPSED}s | ready=${READY} unacked=${UNACK} total=${TOTAL}"

  if [ "$TOTAL" = "0" ]; then
    echo "------------------------------------------"
    echo " Queue Depth 0 도달! 총 소요시간: ${ELAPSED}초"
    echo "=========================================="
    break
  fi

  sleep "$INTERVAL"
done
