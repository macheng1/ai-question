#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
action=${1:-help}
dir=${RUN_DIR:-./run-data}
case "$action" in
  prepare)
    : "${API:?请设置 API，例如 http://服务器:18080/api}"
    if [[ -z ${ADMIN_PHONE:-} ]]; then read -rp '管理员手机号: ' ADMIN_PHONE; fi
    if [[ -z ${ADMIN_PASSWORD:-} ]]; then read -rsp '管理员密码: ' ADMIN_PASSWORD; echo; fi
    export ADMIN_PHONE ADMIN_PASSWORD
    node prepare.mjs "$API" "$dir" "${2:-round1}"
    ;;
  test)
    [[ -f "$dir/batch.json" ]] || { echo '先运行 prepare 或复制已准备的 run-data'; exit 1; }
    api=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).api' "$dir/batch.json")
    id=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).training' "$dir/batch.json")
    users=${2:-1}; seconds=${3:-30}
    [[ "$users" =~ ^[0-9]+$ && "$seconds" =~ ^[0-9]+$ ]] || exit 2
    node training-load.mjs "$api" "$id" "$dir/learners.csv" "$users" "$seconds" "${4:-}" | tee "$dir/result-${id}-${users}-$(date +%Y%m%d-%H%M%S).log"
    ;;
  *) echo '用法: bash run.sh prepare [批次名] 或 bash run.sh test [人数] [每人学习秒数] [入场总秒数]'; exit 2 ;;
esac
