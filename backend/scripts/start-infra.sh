#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
docker compose up -d mysql redis
echo "MySQL 和 Redis 已启动。"
