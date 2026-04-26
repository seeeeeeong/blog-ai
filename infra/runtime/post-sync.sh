#!/bin/bash
set -euo pipefail

echo "=== Post-sync (blog-ai) ==="

mkdir -p /opt/services/{bin,env,data/alloy}

mv -f /opt/services/deploy-service.sh /opt/services/bin/deploy-service.sh 2>/dev/null || true
chmod +x /opt/services/bin/deploy-service.sh /opt/services/post-sync.sh 2>/dev/null || true

# Reconcile shared services — only include alloy if Prometheus creds exist in compose.env
if [ -f /opt/services/compose.env ]; then
  SERVICES="node-exporter"
  if grep -q '^PROMETHEUS_REMOTE_WRITE_URL=..' /opt/services/compose.env 2>/dev/null; then
    SERVICES="$SERVICES alloy"
  fi
  if docker compose version &>/dev/null; then
    docker compose --env-file /opt/services/compose.env -f /opt/services/docker-compose.yml up -d $SERVICES
  else
    docker-compose --env-file /opt/services/compose.env -f /opt/services/docker-compose.yml up -d $SERVICES
  fi
fi

echo "=== Post-sync done ==="
