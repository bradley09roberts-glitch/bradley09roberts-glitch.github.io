#!/usr/bin/env bash
# Set up the football model's always-on server (docs/server.md). Run as root on Ubuntu 24.04;
# deploy/bootstrap.sh runs it the first time. Safe to run again: it keeps the website
# password, the notification topic, the Sportmonks token and data/ it finds, and reinstalls
# packages, units and the web server configuration.
#
#   sudo /home/footy/football-model/football/deploy/setup.sh [option]
#     --rotate-password   a new website password (printed once)
#     --rotate-token      ask for the Sportmonks token again
#     --rotate-topic      a new notification topic (subscribe to it again in the ntfy app)
#     --again             repeat the data bootstrap (deploy/bootstrap-data.sh --again)
#     --skip-bootstrap    leave data/ as it is
set -Eeuo pipefail

REPO_URL=${FOOTY_REPO_URL:-git@github.com:bradley09roberts-glitch/football-model.git}
BRANCH=${FOOTY_BRANCH:-main}
U=footy
H=/home/$U
REPO=$H/football-model
PROJECT=$REPO/football
SITE_DIR=/srv/footy-site
CONF_DIR=$H/.config/footy
STATE_DIR=$H/.local/state/footy
AUTH_USER=owner
AUTH_HASH_FILE=/etc/caddy/footy-auth.hash
UV_VERSION=0.11.32
NODE_MAJOR=22
GITHUB_KEY='github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl'
GITHUB_FP='SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU' # docs.github.com, "GitHub's SSH key fingerprints"

rotate_password=0 rotate_token=0 rotate_topic=0 bootstrap_args=() skip_bootstrap=0
for arg in "$@"; do
  case $arg in
    --rotate-password) rotate_password=1 ;;
    --rotate-token) rotate_token=1 ;;
    --rotate-topic) rotate_topic=1 ;;
    --again) bootstrap_args=(--again) ;;
    --skip-bootstrap) skip_bootstrap=1 ;;
    -h | --help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option $arg (--help)"; exit 2 ;;
  esac
done

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mStopped: %s\033[0m\n' "$*" >&2; exit 1; }
as_footy() { sudo -u "$U" -H -- "$@"; }
# apt-get, waiting while another apt holds its lock: a new server runs its automatic updates
# in its first minutes, and then `apt-get update` fails at once (no option makes it wait)
apt_get() {
  local i out
  for i in $(seq 1 90); do
    out=$(apt-get -o DPkg::Lock::Timeout=600 "$@" 2>&1) && return 0
    if [[ $out != *"Could not get lock"* && $out != *"Unable to lock"* &&
      $out != *"Unable to acquire the dpkg frontend lock"* ]]; then
      printf '%s\n' "$out" >&2
      return 1
    fi
    [[ $i == 1 ]] && echo "Waiting while the server's automatic updates finish (apt is busy) ..."
    sleep 10
  done
  printf '%s\n' "$out" >&2
  return 1
}
trap 'die "line $LINENO: $BASH_COMMAND failed. Fix the cause and run this script again (it is safe to repeat)."' ERR

[[ $(id -u) -eq 0 ]] || die "run this as root: sudo $0"
[[ -r /dev/tty ]] || die "run this from a terminal (it asks two questions)"
cd /
# shellcheck source=/dev/null
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == 24.04 ]] ||
  echo "Warning: written for Ubuntu 24.04; this is ${PRETTY_NAME:-unknown}. Carrying on."
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a

# --- 1. the footy user, its deploy key and the repository (asks first) ----------------------
say "User $U and the GitHub deploy key"
id "$U" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "$U"
apt_get update -qq
apt_get install -y -qq git openssh-client sudo curl ca-certificates gnupg openssl >/dev/null
# shellcheck disable=SC2016 # $1 is the inner script's argument
as_footy bash -c 'set -e; umask 077; mkdir -p ~/.ssh
  [ -f ~/.ssh/id_ed25519 ] || ssh-keygen -q -t ed25519 -N "" -C "football-model server" -f ~/.ssh/id_ed25519
  grep -qxF "$1" ~/.ssh/known_hosts 2>/dev/null || echo "$1" >> ~/.ssh/known_hosts' _ "$GITHUB_KEY"
fp=$(ssh-keygen -lf <(echo "$GITHUB_KEY") | awk '{print $2}')
[[ $fp == "$GITHUB_FP" ]] || die "the pinned GitHub host key has fingerprint $fp, expected $GITHUB_FP"
if ! as_footy git ls-remote "$REPO_URL" HEAD >/dev/null 2>&1; then
  echo
  echo "Add this server's key to the private repository:"
  echo "  1. Open https://github.com/bradley09roberts-glitch/football-model/settings/keys/new"
  echo "  2. Title: football server. Key: the line below. Tick \"Allow write access\". Add key."
  echo
  cat "$H/.ssh/id_ed25519.pub"
  echo
  until as_footy git ls-remote "$REPO_URL" HEAD >/dev/null 2>&1; do
    read -r -p "  3. Press Enter once the key is added: " _ </dev/tty
  done
fi
if [[ ! -d $REPO/.git ]]; then
  as_footy git clone --quiet --branch "$BRANCH" "$REPO_URL" "$REPO"
fi
as_footy git -C "$REPO" config user.name "football-model server"
as_footy git -C "$REPO" config user.email "footy@$(hostname -s).invalid"
can_push() { # a dry-run push reaches GitHub's receive-pack, which refuses a read-only key
  local out
  out=$(as_footy git -C "$REPO" push --dry-run origin "HEAD:refs/heads/$BRANCH" 2>&1) && return 0
  [[ $out == *"[rejected]"* ]] # behind the branch: refused for that, not for the key
}
until can_push; do
  echo "The key can read the repository but not write to it: on GitHub, open Settings > Deploy"
  echo "keys, delete the key, and add it again with \"Allow write access\" ticked."
  read -r -p "Press Enter to try again: " _ </dev/tty
done

# --- 2. the Sportmonks token (asks) -----------------------------------------------------------
# Files in the footy user's folders are written by that user, never by root (root would
# follow a link planted there); the content comes on stdin, so no secret is on a command line.
write_as_footy() { # write_as_footy <path> <content: mode 600, replaced in one step
  # shellcheck disable=SC2016 # the inner script's argument
  as_footy bash -c 'umask 077 && cat >"$1.tmp" && mv -f "$1.tmp" "$1"' _ "$1"
}
env_file=$PROJECT/.env
write_env() { # .env from .env.example; bash builtins only, so the token never reaches a command line
  local token=$1 line key out=""
  declare -A vals=([FOOTY_DISPLAY_TZ]=Europe/London [FOOTY_LOG_LEVEL]=INFO [FOOTY_FD_DOWNLOAD]=true)
  if [[ -n $token ]]; then
    vals[FOOTY_LIVE_PROVIDER]=sportmonks
    vals[SPORTMONKS_API_TOKEN]=$token
    vals[FOOTY_SPORTMONKS_LEAGUES]=${FOOTY_SPORTMONKS_LEAGUES:-SC0:501,DK1:271}
  else
    vals[FOOTY_LIVE_PROVIDER]=none
  fi
  while IFS= read -r line || [[ -n $line ]]; do
    key=${line%%=*}
    if [[ $line != \#* && $line == *=* && -n ${vals[$key]+set} ]]; then
      out+="$key=${vals[$key]}"$'\n'
      unset "vals[$key]"
    else
      out+="$line"$'\n'
    fi
  done <"$PROJECT/.env.example"
  for key in "${!vals[@]}"; do out+="$key=${vals[$key]}"$'\n'; done
  printf '%s' "$out" | write_as_footy "$env_file"
}
if [[ ! -f $env_file || $rotate_token == 1 ]]; then
  say "Sportmonks (odds capture; optional)"
  echo "Paste your Sportmonks API token and press Enter (it isn't shown), or just press Enter"
  echo "to run without it (the predictions don't need it)."
  read -r -s -p "Token: " token </dev/tty
  echo
  token=${token//[[:space:]]/}
  write_env "$token"
  unset token
  echo "Saved in $env_file (readable by $U only)."
fi

# the notification topic now, so the phone can subscribe while the rest runs
# shellcheck disable=SC2016 # the inner script's arguments
as_footy bash -c 'umask 077 && mkdir -p "$@" && chmod 700 "$@"' _ "$CONF_DIR" "$STATE_DIR" "$H/backups"
jobs_env=$CONF_DIR/jobs.env
topic=""
[[ -f $jobs_env ]] && topic=$(sed -n 's/^NTFY_TOPIC=//p' "$jobs_env")
if [[ -z $topic || $rotate_topic == 1 ]]; then
  topic="footy-$(openssl rand -hex 10)"
fi
echo
echo "That's all the questions. The rest takes about 30-40 minutes; the website's password"
echo "is printed at the end. Meanwhile, on your phone: install the free app \"ntfy\", tap +,"
echo "and subscribe to this topic (server: ntfy.sh, the default):"
echo
echo "    $topic"
echo

# --- 3. packages, time zone, firewall, automatic updates ------------------------------------
say "Packages"
apt_get install -y -qq build-essential sqlite3 jq rsync xz-utils ufw fail2ban \
  unattended-upgrades python3 >/dev/null
timedatectl set-timezone Europe/London
cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
cat >/etc/apt/apt.conf.d/52footy-unattended <<'EOF'
// deploy/setup.sh: security updates install by themselves; a reboot they need happens at
// 05:15 UK, when no job is running (the timers catch up anything missed).
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "05:15";
EOF
systemctl enable --now unattended-upgrades >/dev/null 2>&1 || true
systemctl enable --now fail2ban >/dev/null
f2b=0
for _ in 1 2 3 4 5; do
  if fail2ban-client status sshd >/dev/null 2>&1; then f2b=1 && break; fi
  sleep 2
done
[[ $f2b == 1 ]] || echo "Warning: fail2ban's SSH jail isn't running (check: fail2ban-client status sshd)"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=500M\n' >/etc/systemd/journald.conf.d/footy.conf
systemctl restart systemd-journald
if ! swapon --show | grep -q .; then # model fits and the site build together can be large
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi
usermod -aG systemd-journal "$U"

if ! node --version 2>/dev/null | grep -q "^v$NODE_MAJOR\."; then
  say "Node.js $NODE_MAJOR (to build the website)"
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key |
    gpg --dearmor --yes -o /etc/apt/keyrings/nodesource.gpg
  echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_$NODE_MAJOR.x nodistro main" \
    >/etc/apt/sources.list.d/nodesource.list
  apt_get update -qq
  apt_get install -y -qq nodejs >/dev/null
fi
if ! command -v caddy >/dev/null; then
  say "Caddy (HTTPS web server)"
  curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key |
    gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
    >/etc/apt/sources.list.d/caddy-stable.list
  apt_get update -qq
  apt_get install -y -qq caddy >/dev/null
fi
if [[ ! -x $H/.local/bin/uv ]] || ! as_footy "$H/.local/bin/uv" --version | grep -q "$UV_VERSION"; then
  say "uv $UV_VERSION (Python environment)"
  as_footy bash -c "curl -LsSf https://astral.sh/uv/$UV_VERSION/install.sh | UV_NO_MODIFY_PATH=1 sh" >/dev/null
fi

# --- 4. the code: a fast-forward on a re-run, the environment, openfootball's path ----------
say "Project"
if [[ -z $(as_footy git -C "$REPO" status --porcelain --untracked-files=no) ]]; then
  as_footy git -C "$REPO" fetch --quiet origin "+refs/heads/$BRANCH:refs/remotes/origin/$BRANCH"
  as_footy git -C "$REPO" merge --ff-only --quiet "origin/$BRANCH" ||
    echo "Not fast-forwarded (local commits?): the weekly update handles it."
else
  echo "Local changes in $REPO: not updating the code here (the weekly update does it safely)."
fi
as_footy bash -c "cd '$PROJECT' && '$H/.local/bin/uv' sync --frozen --quiet"
# fixtures.py reads the league schedules from /home/user/openfootball; the clones live in
# ~footy/openfootball (where euro-update looks for champions-league).
as_footy mkdir -p "$H/openfootball"
if [[ ! -e /home/user/openfootball ]]; then
  install -d -m 0755 /home/user
  ln -s "$H/openfootball" /home/user/openfootball
fi

# --- 5. the website address, its password and the jobs' settings -------------------
ip4=$(curl -4 -fsS -m 5 http://169.254.169.254/hetzner/v1/metadata/public-ipv4 2>/dev/null || true)
[[ $ip4 =~ ^[0-9]+(\.[0-9]+){3}$ ]] ||
  ip4=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}')
if [[ ! $ip4 =~ ^[0-9]+(\.[0-9]+){3}$ || $ip4 =~ ^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.) ]]; then
  ip4=$(curl -4 -fsS -m 10 https://api.ipify.org || true)
fi
[[ $ip4 =~ ^[0-9]+(\.[0-9]+){3}$ ]] || die "can't find this server's public IPv4 address (keep IPv4 ticked when creating the server)"
HOST=${FOOTY_SITE_HOST:-${ip4//./-}.sslip.io}
SITE_URL="https://$HOST/"
write_as_footy "$jobs_env" <<EOF
# Written by deploy/setup.sh; read by deploy/jobs/lib.sh. Re-run setup.sh to change it.
NTFY_URL=https://ntfy.sh
NTFY_TOPIC=$topic
FOOTY_SITE_URL=$SITE_URL
FOOTY_SITE_DIR=$SITE_DIR
FOOTY_STATE_DIR=$STATE_DIR
FOOTY_BACKUP_DIR=$H/backups
FOOTY_OPENFOOTBALL=$H/openfootball
FOOTY_RECORDS_REF=refs/remotes/origin/$BRANCH
FOOTY_BRANCH=$BRANCH
EOF

password=""
if [[ ! -s $AUTH_HASH_FILE || $rotate_password == 1 ]]; then
  # four groups of five from 31 letters and digits that can't be confused (about 99 bits)
  password=$(python3 -I -c 'import secrets
a = "abcdefghjkmnpqrstuvwxyz23456789"
print("-".join("".join(secrets.choice(a) for _ in range(5)) for _ in range(4)))')
  hash=$(printf '%s\n' "$password" | caddy hash-password)
  [[ $hash == \$2* ]] || die "caddy hash-password gave no bcrypt hash"
  (umask 027 && printf '%s\n' "$hash" >"$AUTH_HASH_FILE")
  chgrp caddy "$AUTH_HASH_FILE"
fi
hash=$(head -n 1 "$AUTH_HASH_FILE")

# --- 6. the website ---------------------------------------------------------------------------
say "Website build"
install -d -o "$U" -g "$U" -m 0755 "$SITE_DIR"
as_footy env FOOTY_SITE_DIR="$SITE_DIR" bash "$PROJECT/deploy/build-site.sh"

# --- 7. data --------------------------------------------------------------------------------------
first_run=0
[[ -e $STATE_DIR/bootstrap.done ]] || first_run=1
if [[ $skip_bootstrap == 0 ]]; then
  say "Data: rebuilding data/ from the sources and the records (about 20-30 minutes)"
  as_footy bash "$PROJECT/deploy/bootstrap-data.sh" "${bootstrap_args[@]}" ||
    die "the data bootstrap stopped (the reason is above). Fix it and run this script again."
fi

# --- 8. services, timers, sudo rule, web server -------------------------------------------------
say "Services and timers"
# checked in place with its final owner and mode (sudo ignores names with a dot meanwhile)
install -m 0440 -o root -g root "$PROJECT/deploy/sudoers-footy" /etc/sudoers.d/.footy.new
visudo -cf /etc/sudoers.d/.footy.new >/dev/null
mv /etc/sudoers.d/.footy.new /etc/sudoers.d/footy
units=()
for f in "$PROJECT"/deploy/systemd/*; do
  name=$(basename "$f")
  sed -e "s|@USER@|$U|g" -e "s|@HOME@|$H|g" -e "s|@PROJECT@|$PROJECT|g" "$f" \
    >"/etc/systemd/system/$name.tmp"
  mv "/etc/systemd/system/$name.tmp" "/etc/systemd/system/$name"
  units+=("$name")
done
systemctl daemon-reload
systemctl enable --now footy-api.service >/dev/null 2>&1
systemctl restart footy-api.service
for name in "${units[@]}"; do
  if [[ $name == *.timer ]]; then systemctl enable --now "$name" >/dev/null 2>&1; fi
done

caddy_file=/etc/caddy/Caddyfile
sed -e "s|@HOST@|$HOST|g" -e "s|@SITE_DIR@|$SITE_DIR|g" -e "s|@AUTH_USER@|$AUTH_USER|g" \
  -e "s|@AUTH_HASH@|$hash|g" "$PROJECT/deploy/Caddyfile.template" >"$caddy_file.new"
minor=$(caddy version | sed -n 's/^v2\.\([0-9]*\)\..*/\1/p')
if [[ -n $minor && $minor -lt 8 ]]; then sed -i 's/basic_auth {/basicauth {/' "$caddy_file.new"; fi
chgrp caddy "$caddy_file.new" && chmod 640 "$caddy_file.new"
# validated as the caddy user, so any log file it opens belongs to caddy
sudo -u caddy caddy validate --config "$caddy_file.new" --adapter caddyfile >/dev/null 2>&1 || {
  sudo -u caddy caddy validate --config "$caddy_file.new" --adapter caddyfile || true
  die "the Caddy configuration is invalid"
}
mv "$caddy_file.new" "$caddy_file"
chown -R caddy:caddy /var/log/caddy
systemctl enable caddy >/dev/null 2>&1
systemctl reload caddy 2>/dev/null || systemctl restart caddy

if [[ $first_run == 1 && -e $STATE_DIR/bootstrap.done ]]; then
  # a new server: publish what is due now rather than at the next 08:00 (in the background)
  systemctl start --no-block footy-daily.service
fi

# --- 9. checks and the summary -------------------------------------------------------------------
say "Checking the website (the certificate can take a minute)"
site_ok=0
for _ in $(seq 1 24); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "${SITE_URL}api/health" || true)
  if [[ $code == 401 ]]; then site_ok=1; break; fi
  sleep 5
done
if [[ -n $password && $site_ok == 1 ]]; then
  # with the password, through a config on stdin (never on a command line); the API may
  # still be starting
  for _ in $(seq 1 12); do
    code=$(printf 'user = "%s:%s"\n' "$AUTH_USER" "$password" |
      curl -s -o /dev/null -w '%{http_code}' -m 20 -K - "${SITE_URL}api/health" || true)
    [[ $code == 200 ]] && break
    sleep 5
  done
  [[ $code == 200 ]] || site_ok=0
fi
as_footy env NTFY_TOPIC="$topic" bash -c "source '$PROJECT/deploy/jobs/lib.sh'; JOB=setup;
  notify 'Football server ready' 'Notifications from your football server will arrive here.' 3 tada" ||
  true
doctor=$(as_footy bash -c "source '$PROJECT/deploy/jobs/lib.sh'; footy doctor --quiet --web" 2>&1 || true)
timers=$(systemctl list-timers 'footy-*' --no-pager 2>/dev/null | head -n 12 || true)
indent=$'\n   '
timers=${timers//$'\n'/$indent}
doctor=${doctor//$'\n'/$indent}

cat <<EOF


==============================================================================
 Your football model server is set up.

 Website:   $SITE_URL
 Username:  $AUTH_USER
EOF
if [[ -n $password ]]; then
  cat <<EOF
 Password:  $password
            Shown only this once: save it in your phone's password manager now.
            (A new one: sudo $PROJECT/deploy/setup.sh --rotate-password)
EOF
else
  echo " Password:  unchanged (a new one: sudo $PROJECT/deploy/setup.sh --rotate-password)"
fi
cat <<EOF
 Website check: $([[ $site_ok == 1 ]] && echo "OK, HTTPS and the password work" || echo "NOT OK yet: see docs/server.md, 'If the website doesn't open'")

 Phone notifications: install the free "ntfy" app, tap +, and subscribe to
     topic:  $topic
     server: https://ntfy.sh (the default)
 Keep the topic name private: anyone who knows it can read the messages.

 Scheduled jobs (UK time):
   $timers

 Health check:
   $doctor
==============================================================================
EOF
