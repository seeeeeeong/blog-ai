#!/bin/bash
set -euo pipefail

SERVICE="${1:?Usage: deploy-service.sh <blog-ai> <ecr-image>}"
NEW_IMAGE="${2:?Usage: deploy-service.sh <blog-ai> <ecr-image>}"

RUNTIME_DIR="/opt/services"
COMPOSE_FILE="$RUNTIME_DIR/docker-compose.yml"
COMPOSE_ENV="$RUNTIME_DIR/compose.env"
ENV_DIR="$RUNTIME_DIR/env"
AWS_REGION="${SSM_AWS_REGION:-ap-northeast-2}"
BLOG_AI_SSM="${BLOG_AI_SSM_PREFIX:-/blog-ai/prod}"

case "$SERVICE" in
  blog-ai) ;;
  *) echo "Unknown service: $SERVICE (allowed: blog-ai)"; exit 1 ;;
esac

# --- Helpers ---
compose() {
  if docker compose version &>/dev/null; then
    docker compose --env-file "$COMPOSE_ENV" -f "$COMPOSE_FILE" "$@"
  else
    docker-compose --env-file "$COMPOSE_ENV" -f "$COMPOSE_FILE" "$@"
  fi
}

ssm_get() { aws ssm get-parameter --region "$AWS_REGION" --name "$1" --with-decryption --query Parameter.Value --output text 2>/dev/null; }
ssm_get_or() { ssm_get "$1" 2>/dev/null || printf '%s' "$2"; }

wait_healthy() {
  local container="$1" retries="${2:-60}"
  for i in $(seq 1 "$retries"); do
    local s; s=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{if .State.Running}}running{{else}}stopped{{end}}{{end}}' "$container" 2>/dev/null || echo "missing")
    echo "  [$i/$retries] $container=$s"
    [[ "$s" == "healthy" || "$s" == "running" ]] && return 0
    sleep 5
  done
  return 1
}

fail_diagnostics() {
  local c="$1"
  echo "--- $c diagnostics ---"
  docker logs --tail 100 "$c" 2>/dev/null || true
}

write_env() {
  local tmp; tmp=$(mktemp)
  cat > "$tmp"; chmod 600 "$tmp"; mv "$tmp" "$1"
}

# --- Setup ---
echo "=== Deploy: $SERVICE ($NEW_IMAGE) ==="
mkdir -p "$RUNTIME_DIR/bin" "$ENV_DIR" "$RUNTIME_DIR/data/alloy"

REGISTRY="${NEW_IMAGE%%/*}"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

# --- blog-ai deploy ---
write_env "$ENV_DIR/blog-ai.env" <<EOF
SPRING_PROFILES_ACTIVE=prod
DB_HOST=$(ssm_get "$BLOG_AI_SSM/DB_HOST")
DB_PORT=$(ssm_get_or "$BLOG_AI_SSM/DB_PORT" 5432)
DB_NAME=$(ssm_get_or "$BLOG_AI_SSM/DB_NAME" blog_ai)
DB_USERNAME=$(ssm_get_or "$BLOG_AI_SSM/DB_USERNAME" postgres)
DB_PASSWORD=$(ssm_get "$BLOG_AI_SSM/DB_PASSWORD")
OPENAI_API_KEY=$(ssm_get "$BLOG_AI_SSM/OPENAI_API_KEY")
CORS_ORIGINS=$(ssm_get_or "$BLOG_AI_SSM/CORS_ORIGINS" "")
ADMIN_API_KEY=$(ssm_get "$BLOG_AI_SSM/ADMIN_API_KEY")
INTERNAL_API_KEY=$(ssm_get "$BLOG_AI_SSM/INTERNAL_API_KEY")
SLACK_WEBHOOK_URL=$(ssm_get_or "$BLOG_AI_SSM/SLACK_WEBHOOK_URL" "")
JINA_API_KEY=$(ssm_get_or "$BLOG_AI_SSM/JINA_API_KEY" "")
SENTRY_DSN=$(ssm_get_or "$BLOG_AI_SSM/SENTRY_DSN" "")
SENTRY_ENVIRONMENT=$(ssm_get_or "$BLOG_AI_SSM/SENTRY_ENVIRONMENT" prod)
APP_VERSION=$(ssm_get_or "$BLOG_AI_SSM/APP_VERSION" unknown)
JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -XX:MaxMetaspaceSize=150m
EOF

write_env "$COMPOSE_ENV" <<EOF
BLOG_AI_IMAGE=$NEW_IMAGE
PROMETHEUS_REMOTE_WRITE_URL=$(ssm_get_or "$BLOG_AI_SSM/PROMETHEUS_REMOTE_WRITE_URL" "")
PROMETHEUS_USERNAME=$(ssm_get_or "$BLOG_AI_SSM/PROMETHEUS_USERNAME" "")
PROMETHEUS_PASSWORD=$(ssm_get_or "$BLOG_AI_SSM/PROMETHEUS_PASSWORD" "")
EOF

compose up -d --no-deps --force-recreate blog-ai
wait_healthy blog-ai || { fail_diagnostics blog-ai; exit 1; }

compose up -d node-exporter alloy
docker image prune -f &>/dev/null || true
echo "=== $SERVICE deployed ==="
