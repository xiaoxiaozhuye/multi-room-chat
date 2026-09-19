#!/usr/bin/env bash
set -eu

java -jar /app/app.jar &
backend_pid=$!

cleanup() {
  kill "$backend_pid" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

nginx -g 'daemon off;' &
nginx_pid=$!

wait -n "$backend_pid" "$nginx_pid"
exit $?
