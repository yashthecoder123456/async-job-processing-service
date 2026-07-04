#!/usr/bin/env bash
# Prepare Cursor/devcontainer workspace for DigitalOcean deploy (no sudo required).
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS_DIR="$ROOT_DIR/.tools/bin"
mkdir -p "$TOOLS_DIR"

if ! command -v terraform >/dev/null && [[ ! -x "$TOOLS_DIR/terraform" ]]; then
  ARCH=$(uname -m)
  case "$ARCH" in
    aarch64|arm64) TFARCH=arm64 ;;
    x86_64|amd64) TFARCH=amd64 ;;
    *) echo "Unsupported arch: $ARCH"; exit 1 ;;
  esac
  echo "Installing Terraform to $TOOLS_DIR ..."
  curl -fsSL "https://releases.hashicorp.com/terraform/1.9.8/terraform_1.9.8_linux_${TFARCH}.zip" -o /tmp/terraform.zip
  unzip -o /tmp/terraform.zip -d "$TOOLS_DIR"
fi

SSH_DIR="${HOME}/.ssh"
mkdir -p "$SSH_DIR"
chmod 700 "$SSH_DIR"
if [[ ! -f "$SSH_DIR/id_rsa" ]]; then
  echo "Generating SSH key at $SSH_DIR/id_rsa ..."
  ssh-keygen -t rsa -b 4096 -f "$SSH_DIR/id_rsa" -N "" -q
fi

echo ""
echo "Cursor workspace ready for DigitalOcean deploy."
echo "  Terraform: $("$TOOLS_DIR/terraform" version | head -1)"
echo "  doctl:     $(doctl version 2>/dev/null | head -1 || echo 'install doctl')"
echo "  SSH key:   $SSH_DIR/id_rsa.pub"
echo ""
echo "Next:"
echo "  1. cp .env.do.example .env.do"
echo "  2. Edit .env.do in Cursor (add DIGITALOCEAN_ACCESS_TOKEN + RABBITMQ_PASSWORD)"
echo "  3. ./scripts/do/deploy-all.sh"
