#!/usr/bin/env bash
set -euo pipefail
exec python3 "$(dirname "$0")/agp_test_server.py" "$@"
