#!/usr/bin/env bash
# First step on a new server (docs/server.md): pasted into the server's console as root.
# Makes the footy user and its GitHub deploy key, waits while the key is added to the private
# repository, clones it, then runs deploy/setup.sh from the clone (which does the rest).
set -euo pipefail
REPO_URL=git@github.com:bradley09roberts-glitch/football-model.git
GITHUB_KEY='github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl'
[ "$(id -u)" -eq 0 ] || { echo "Run this as root (or with sudo)"; exit 1; }
cd /
export DEBIAN_FRONTEND=noninteractive
if ! command -v git >/dev/null || ! command -v ssh-keygen >/dev/null || ! command -v sudo >/dev/null; then
  until apt-get update -qq; do echo "apt is busy (automatic updates): trying again in 20 s"; sleep 20; done
  apt-get -o DPkg::Lock::Timeout=900 install -y -qq git openssh-client sudo >/dev/null
fi
id footy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash footy
sudo -u footy -H bash -c 'set -e; umask 077; mkdir -p ~/.ssh
  [ -f ~/.ssh/id_ed25519 ] || ssh-keygen -q -t ed25519 -N "" -C "football-model server" -f ~/.ssh/id_ed25519
  grep -qxF "$1" ~/.ssh/known_hosts 2>/dev/null || echo "$1" >> ~/.ssh/known_hosts' _ "$GITHUB_KEY"
echo
echo "1. Open https://github.com/bradley09roberts-glitch/football-model/settings/keys/new"
echo "2. Title: football server. Key: paste the line below. Tick \"Allow write access\". Add key."
echo
cat /home/footy/.ssh/id_ed25519.pub
echo
until sudo -u footy -H git ls-remote "$REPO_URL" HEAD >/dev/null 2>&1; do
  read -r -p "3. Press Enter once the key is added: " _ </dev/tty
done
[ -d /home/footy/football-model/.git ] ||
  sudo -u footy -H git clone --quiet --branch main "$REPO_URL" /home/footy/football-model
exec bash /home/footy/football-model/football/deploy/setup.sh "$@"
