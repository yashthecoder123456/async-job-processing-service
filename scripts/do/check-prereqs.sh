#!/usr/bin/env bash
set -euo pipefail

missing=0
for cmd in doctl terraform docker; do
  if ! command -v "$cmd" >/dev/null; then
    echo "Missing required command: $cmd"
    missing=1
  fi
done

if [[ -z "${DIGITALOCEAN_ACCESS_TOKEN:-}" ]]; then
  echo "DIGITALOCEAN_ACCESS_TOKEN is not set"
  missing=1
fi

if [[ $missing -ne 0 ]]; then
  exit 1
fi

echo "Prerequisites satisfied"
