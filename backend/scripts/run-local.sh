#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then cp .env.example .env; echo "已创建 backend/.env，请按需修改后重新运行。"; fi
set -a
source .env
set +a
if [[ -x ./mvnw ]]; then ./mvnw spring-boot:run; else mvn spring-boot:run; fi
