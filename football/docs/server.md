# The always-on server

A small rented Linux server runs the football model around the clock: it fetches results and
prices, publishes the genuine pre-match records before kick-off, settles them, pushes them to
the private GitHub repository, backs up the database, and serves the website to your phone
behind a password. It sends a push notification when something goes wrong, when the week's
prices are in, and a short summary every Monday. (DECISIONS.md D-009; audit H1, H3, M5, M6,
D1, D2.)

The first part is for you, the owner. The second part ("Operator") is the technical reference.

---

## Part 1: setting it up (owner)

You do four things: create the server, paste one command, add a key on GitHub, and subscribe
to the notifications on your phone. Nobody else creates accounts or buys anything for you.

### What it costs

About **€7 to €9 a month** (roughly £6 to £8) at Hetzner for the server, its backups and its
internet address, billed by the hour, so deleting it stops the cost at once. Hetzner shows the
exact monthly total before you confirm. Nothing else is paid: the web address, the HTTPS
certificate and the notifications are free.

### 1. Create the server (Hetzner Cloud)

1. Go to <https://www.hetzner.com/cloud> and create an account (they may ask for ID or a
   card check). Open the **Cloud Console** and create a **project**, for example "football".
2. In the project, click **Add Server** (or **Create Server**) and choose:
   - **Location:** any in Europe (Falkenstein, Nuremberg or Helsinki).
   - **Image:** **Ubuntu 24.04**.
   - **Type:** **Shared vCPU**, **x86 (Intel/AMD)**, the plan with **4 vCPU and 8 GB RAM**
     (in 2025 it was called CX32 or CX33; the name changes now and then, the size is what
     matters).
   - **Networking:** keep **Public IPv4** ticked (phones on some networks can't reach an
     IPv6-only server) and IPv6 too.
   - **SSH keys:** skip this if you don't know what it is. Hetzner then emails you a root
     password.
   - **Volumes, Firewalls, Placement groups, Labels, Cloud config:** leave empty. The server
     sets up its own firewall.
   - **Backups:** **tick it** (about 20% on top). Hetzner then keeps a copy of the whole
     server from each of the last 7 days.
   - **Name:** for example `football`.
3. Click **Create & Buy now**. After a minute the server shows its **IPv4 address** (four
   numbers, like `203.0.113.5`).

### 2. Log in to the server from a computer and paste the command

Use a computer for this (Mac or Windows; nothing to install). In step 3 you copy a long line
*out of* the server's window, and Hetzner's browser console can't do that.

1. Open **Terminal** (Mac: Applications > Utilities > Terminal) or **PowerShell** (Windows:
   Start menu, type `PowerShell`).
2. Type `ssh root@203.0.113.5` with your server's address and press Enter. Answer `yes` to
   the question about the server's fingerprint.
3. Give the root password from Hetzner's email. **Nothing appears while you type a
   password**; that's normal, press Enter at the end. The first time, the server asks for
   that password once more and then for a new one, twice. Choose a long one and keep it in
   your password manager: it is the key to the whole server.
4. When you see a line ending in `#`, you are in.

Then copy this whole block, paste it in one go (Mac: Cmd+V; Windows: right-click, and if
Windows asks about pasting several lines, choose **Paste anyway**), and press Enter:

```bash
cat > /root/footy-bootstrap.sh <<'FOOTY_BOOTSTRAP'
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
FOOTY_BOOTSTRAP
bash /root/footy-bootstrap.sh
```

(The block is `deploy/bootstrap.sh` from the repository. The repository is private, so the
server can't download it before it has a key; that is why it is pasted.)

### 3. Add the server's key on GitHub

The command prints a long line starting `ssh-ed25519` and ending `football-model server`.
That is the server's key, which lets it read the private repository and push the published
records to it. It works for this one repository only.

1. Copy the whole line: select it with the mouse from `ssh-ed25519` to `server`, then
   copy it (Mac: Cmd+C; Windows: right-click, which copies a selection).
2. Open the link it shows, logged in to GitHub as `bradley09roberts-glitch` (the page is the
   repository's **Settings > Deploy keys > Add deploy key**).
3. **Title:** `football server`. **Key:** paste the line.
4. Tick **Allow write access**. Click **Add key**.
5. Back in the server's window, press **Enter**. If it asks again, the key isn't added yet
   (or was added without write access: it says so); fix it on GitHub and press Enter again.

### 4. Two questions, then wait

- **Sportmonks token:** paste your Sportmonks API token and press Enter (it isn't shown as you
  paste), or just press Enter to skip it. It is only used for the odds snapshots; the
  predictions don't need it.
- The window then shows a **notification topic** (a name like `footy-3f9c...`). Use it in
  step 5 while the rest runs.

The rest takes about 30 to 40 minutes: it installs everything, rebuilds the data from the
public sources, restores every published record, builds the website and starts the jobs.
**Keep the window open and the computer awake until it finishes** (plug a laptop in). At
the end it prints a box with:

- **Website:** an address like `https://203-0-113-5.sslip.io/` (sslip.io turns the server's
  number into a name, so no domain has to be bought; the certificate is a normal free one).
- **Username:** `owner`.
- **Password:** four groups of five letters and digits. **It is shown only this once:** save
  it in your phone's password manager straight away.

If the window closes or the computer sleeps before the end, nothing is lost: log in again
(`ssh root@...`) and run `sudo /home/footy/football-model/football/deploy/setup.sh` (it
picks up where it was; add `--rotate-password` if you missed the password). Hetzner's browser
console (the server's `>_` icon in the Cloud Console) also works for this, since nothing has
to be copied out of it.

### 5. Notifications on your phone (ntfy, free, no account)

1. Install **ntfy** from the App Store or Google Play.
2. Tap **+** (Subscribe to topic), type the topic name the console showed, keep the server
   as `ntfy.sh`, and tap **Subscribe**.
3. At the end of the set-up you get "Football server ready".

You will get a message when a job fails (with the reason), when Tuesday's and Friday's prices
are in and the records are published (or when they never came), from the daily health check
when something needs attention, and a weekly summary on Monday mornings. Expect a few in the
first days (for example while Wikipedia refuses the server, below): each says what failed. Keep the topic name
private: anyone who knows it can read the messages (they hold no passwords, prices or tokens).

### 6. Open the website

Open the address on your phone. The browser asks for a username and password: `owner` and the
password from step 4. Add it to your home screen if you like. The phone copy (the private
artifact) keeps working as before; the server's site is always current.

### If the website doesn't open

- Give it five minutes after the set-up: the first certificate can take a moment.
- Check the address: dashes, not dots, between the numbers, then `.sslip.io`.
- On the server, run `sudo /home/footy/football-model/football/deploy/setup.sh` again: it
  repeats every step safely and prints the check at the end.

### How to stop it

- **Pause the cost for good:** in the Hetzner Cloud Console, open the server, **Delete**.
  Billing stops at once. The published records are safe in GitHub; the server's database and
  its Hetzner backups go with it (before deleting, you can turn a backup into a snapshot,
  which costs a little per month, to keep it).
- **Power off** doesn't stop the bill (Hetzner charges for a stopped server too).
- **Change the password:** `sudo /home/footy/football-model/football/deploy/setup.sh
  --rotate-password` (prints the new one once).

---

## Part 2: Operator

### Layout

| Where | What |
| --- | --- |
| `/home/footy/football-model` | the private repository (branch `main`); the project is its `football/` folder |
| `football/data/` | database `footy.sqlite3`, raw store, ledgers (git-ignored) |
| `football/.env` | settings and the Sportmonks token (mode 600) |
| `football/records/predictions/*.jsonl` | the published records, pushed to GitHub by the jobs |
| `/home/footy/openfootball/` | openfootball clones (league schedules, UEFA history); `/home/user/openfootball` links to it because `src/footy/fixtures.py` reads that path |
| `/home/footy/.config/footy/jobs.env` | the jobs' settings: ntfy topic, site address, folders |
| `/home/footy/.local/state/footy/` | job state: `last-start-`, `last-success-`, `last-failure-<job>`, `last-records-push`, `warn-<topic>` notes, `records-import-pending` (a records import to retry), `locks/`, `bootstrap.done` |
| `/home/footy/backups/` | nightly database backups (14 days) and pre-migration backups |
| `/srv/footy-site/` | the built website Caddy serves |
| `/etc/caddy/Caddyfile`, `/etc/caddy/footy-auth.hash` | web server configuration (from `deploy/Caddyfile.template`) and the password's bcrypt hash |
| `/etc/systemd/system/footy-*` | units and timers (from `deploy/systemd/`, `@...@` filled in) |
| `/etc/sudoers.d/footy` | lets `footy` restart the API, nothing else |

Everything runs as the unprivileged user `footy`; `setup.sh` (root) writes the files in
`footy`'s folders (`.env`, `jobs.env`) as `footy`, so a link planted there can't redirect a
root write. The API listens on 127.0.0.1:8000 only; Caddy serves HTTPS on 443 (and redirects
80), with one password (HTTP basic auth) in front of the site, `/api` and `/media`. The
firewall (ufw) allows 22, 80 and 443; fail2ban watches SSH; security updates install
themselves (unattended-upgrades) and reboot at 05:15 UK if needed. The time zone is
Europe/London. `setup.sh` waits while the new server's own first automatic updates hold apt's
lock (they often run in its first minutes).

### Scheduled jobs (UK time, summer and winter)

Each timer starts a oneshot service that runs `deploy/jobs/<job>.sh`. The scripts share
`deploy/jobs/lib.sh`: logging to the journal, a lock so jobs never overlap (the odds capture
has its own lock, so a 25-minute publishing run doesn't stop it; the update takes both), a
push notification when a step fails (with its last lines), and the records push. A job carries
on after a failed step where the next steps don't depend on it, and reports every failure at
the end. The models come from the reference files at run time (`deploy/jobs/plan.py`), so
`footy set-model-of-record` reaches the jobs without editing them.

| Timer | When | Does |
| --- | --- | --- |
| `footy-capture-odds` | every 5 minutes | `footy capture-odds` (does nothing without a provider key); failures notified at most every 6 hours; a run while the weekly update holds the capture lock is skipped, not failed |
| `footy-daily` | 08:00 | `fd-update`, refresh openfootball, `settle`, records sync (below), fallback records of record for new league fixtures (`predict-domestic --model <pure model of record> --days 7`), UEFA records of record (`predict-euro --model pan_euro_mix_mxg --days 3`), push, `publish-shadows` for every shadow model without prices (both groups; retried once), push, `footy doctor` |
| `footy-prices` | Tue and Fri 12:40, retries 14:20 and 15:20 | `fd-fixtures`; once the file published **today** is in: `settle`, `fd-update --scope core`, records sync, fallback records, a dry run then the model + market records (`predict-domestic --model market_tilt_shots`, Tue `--days 3`, Fri `--days 4`), push, `publish-shadows --group domestic`, push, `settle`; "prices in" notification. A retry forces the download (the file's 6-hour limit counts from its first download); after the last try, "no prices today" |
| `footy-uefa-night` | Tue to Thu 23:30 | UEFA results: `euro-update --fetch-wikipedia --no-rebuild`, and only if no page was refused or failed, `euro-update` (rebuild from the fresh pages); `settle`. A refused fetch fails the job, so it is notified (audit M3) |
| `footy-monday` | Mon 08:10 | `fd-update` (all divisions), UEFA as above, `settle`, push, retry team pictures if the bootstrap couldn't get them, weekly summary (`footy doctor --summary` and `/api/performance`) |
| `footy-backup` | 03:30 | `sqlite3 .backup` to `~/backups/footy-<date>.sqlite3.gz` (checked with `PRAGMA quick_check`), 14 days kept; push any records left |
| `footy-update` | Wed 04:00 | code update with a migration guard (below) |

Persistent timers catch up a run missed while the server was off. A job unit that fails
without notifying (killed at its time limit, out of memory) triggers `footy-failure@`, which
notifies with the unit's last journal lines.

### Logs and running a job by hand

```bash
systemctl list-timers 'footy-*'                 # next and last runs
journalctl -u footy-daily -n 200 --no-pager     # one job's log (any footy-<job>)
journalctl -u footy-api -f                      # the API, live
sudo systemctl start footy-prices               # run a job now (as its timer would)
sudo -u footy -H bash -lc 'cd ~/football-model/football && uv run footy doctor'
```

### `footy doctor`

`uv run footy doctor [--quiet] [--json] [--web] [--summary]` prints one line per check (exit 1
on a problem): the schema is at head; the last successful run of every command in `job_runs`
(and a failed latest run, and runs a kill left "running"); the last success of every timer;
standing warnings the jobs left (`warn-*`, e.g. Wikipedia not answering since the bootstrap);
records past kick-off + 3 h still unsettled (a problem after 4 days for league games, 2 days
for UEFA); failed requests in the last 48 hours by source and HTTP status; that the records
files and the database agree and that every records file equals the pushed branch; free disk;
and the API and public site answer (when `FOOTY_SITE_URL` is set). The daily job runs it and
notifies its problems (the same problems at most once a day).

### Records: how they reach GitHub

Publishing appends to `records/predictions/<date>.jsonl`. After each publishing step the job
fetches `main`, builds a commit on top of it with the records files exactly as they are on the
server (git plumbing: the working tree's own commit and code are untouched, so code only
changes through the weekly update), and pushes it.

Before publishing (and before each push) the job syncs the records with `main`
(`sync_records` in `deploy/jobs/lib.sh`): where `main`'s copy of a records file extends the
server's (the disk was restored from an older backup, or the clone was made before a later
push), the server takes `main`'s copy and runs `footy import-records`, which loads the new
lines with their ids and checks every hash. So after a restore nothing is published twice or
under an id already used. Where a file differs both ways (records published on two machines
at once), the job publishes and pushes nothing and notifies; resolve it by hand. Never
publish records from another machine while the server runs.

### Updating the code

Weekly (Wednesday 04:00), or now with `sudo systemctl start footy-update`:

1. push any records not yet pushed; refuse if the working tree has other local changes;
2. fast-forward to `origin/main`, `uv sync --frozen`; if that fails (a network error), put
   the old code back, keeping the records files as pushed, and notify;
3. migration guard (audit M5): if the code expects a newer schema, back up the database to
   `~/backups/pre-migrate-<time>-<old revision>.sqlite3.gz`, then `footy migrate`; a failed
   migration is notified with priority;
4. rebuild the website if `frontend/` changed; restart the API; health check; notification.

If the update changed `deploy/systemd/`, the Caddy template, the sudoers file or `setup.sh`,
the notification asks for `sudo /home/footy/football-model/football/deploy/setup.sh` (root
files aren't changed by the `footy` user).

### Backups and restore

- **Nightly** database copies on the server (14 days), **Hetzner's backup** of the whole disk
  (7 days, if ticked), and the **records in GitHub** (every published record, with its hash).
- **Restore the database from a nightly copy:**

  ```bash
  sudo systemctl stop 'footy-*.timer' footy-api
  sudo -u footy -H bash -lc '
    cd ~/football-model/football &&
    gunzip -c ~/backups/footy-2026-11-03.sqlite3.gz > data/restore.sqlite3 &&
    sqlite3 data/restore.sqlite3 "PRAGMA quick_check" &&
    mv data/footy.sqlite3 data/footy.sqlite3.broken && rm -f data/footy.sqlite3-wal data/footy.sqlite3-shm &&
    mv data/restore.sqlite3 data/footy.sqlite3 &&
    uv run footy migrate && uv run footy import-records && uv run footy doctor'
  sudo systemctl start footy-api 'footy-*.timer'
  ```

  `import-records` puts back any record published after the backup (it keeps every id and
  checks every hash) and settles.
- **Lose the whole server:** create a new one and follow Part 1 again. The bootstrap rebuilds
  `data/` from the sources, restores every record from GitHub (`footy import-records`, audit
  H4), and unpacks the ledgers and reports from the bootstrap archive. Lost for good: the job
  and fetch history (`job_runs`, `fetch_log`), settlement times and odds snapshots, unless a
  Hetzner backup or snapshot is restored instead (Cloud Console > server > Backups > Restore).
  A restored disk is up to a day old: its first job takes the records pushed since from
  GitHub before it publishes anything (records sync, above).

### Bootstrap data

`deploy/bootstrap-data.sh` (run by `setup.sh` as `footy`; `--again` repeats it):
`footy migrate`; openfootball clones; the bootstrap archive from the repository's orphan
branch `bootstrap-data` (parts checked against `SHA256SUMS`, joined, checked, unpacked without
overwriting, every file checked against `FILES.sha256`); `footy fd-update` (all seasons,
retried); UEFA (Wikipedia if it answers the server, otherwise the archived 2026/27 CSV and a
standing warning: the 2025/26 Europa and Conference League history then waits for the first
successful fetch); team pictures if Commons answers; `footy import-records` (it stops with a
clear message if the code lacks it); `footy settle`; `footy doctor` (printed only: the API
and the web server start after the bootstrap).

The archive holds `data/backtests`, `data/backtests_euro`, `data/reports`, `data/lab` and
`data/europe`: ledgers of model probabilities (no price columns, checked), reports and the
2026/27 UEFA CSV. Never `data/raw` (football-data's files carry bookmakers' prices), the
database, media, logs or UEFA.com content (D-023: the script leaves out
`europe/uefa_2627_crosscheck.csv`, written from UEFA.com pages before 22 Oct, and refuses any
CSV with a `uefa_*` column). The model + market ledgers' probabilities are close to the
bookmakers' de-vigged consensus match by match, so the branch must stay in the private
repository. To make or refresh it, on a machine with the data:

```bash
deploy/make-bootstrap-archive.sh /tmp/bootstrap      # refuses on any price-like column or secret
cd /tmp/bootstrap
git init -q -b bootstrap-data && git add -A && git commit -qm "Bootstrap data"
git push --force git@github.com:bradley09roberts-glitch/football-model.git bootstrap-data
```

### Rotating secrets

| What | How |
| --- | --- |
| Website password | `sudo .../deploy/setup.sh --rotate-password` (new one printed once) |
| Sportmonks token | `sudo .../deploy/setup.sh --rotate-token` (asks again; empty turns the capture off) |
| Notification topic | `sudo .../deploy/setup.sh --rotate-topic`, then subscribe to the new one |
| Deploy key | on GitHub delete it; on the server `sudo -u footy rm ~footy/.ssh/id_ed25519*`; run `setup.sh`, which makes a new one and waits for it |

### Testing the jobs (any machine)

`deploy/test-jobs.sh <empty folder>` runs every job against a copy: a scratch clone with a
fake HOME, a local bare repository as the remote, a copy of the database and raw store, a
local stand-in for ntfy, `FOOTY_JOBS_OFFLINE=1` (no outside requests) and no root. It also
checks the retry and give-up paths of the prices job, the update's migration guard (with a
test migration) and its refusal on local changes, the records push refusing a remote that
isn't a prefix of the server's records, a disk restored from an older backup (the next job
takes the pushed records back before publishing, so nothing is published twice), an update
whose `uv sync` fails (the old code comes back, the records stay as pushed), failure
notifications and their rate limit, the locks, and the odds capture skipping while the
update holds its lock.

`deploy/test-bootstrap.sh <empty folder> <archive folder>` rehearses a new server's data
bootstrap: it pushes the archive to a local bare repository's `bootstrap-data` branch with
the commands above, clones it with an empty `data/`, and runs `deploy/bootstrap-data.sh`
offline (football-data's files go in the copy's inbox, Wikipedia is treated as refusing).

### Known limits

- Wikimedia refused this project's previous machine (HTTP 403 from Wikipedia since 21 Oct;
  audit M3). If it refuses the server too, UEFA results need `footy euro-import-results`
  (results from two news publishers; DECISIONS.md D-029) until it answers.
- `src/footy/fixtures.py` reads the league schedules from `/home/user/openfootball` (a fixed
  path); the server links that path to `~footy/openfootball`.
- Only the nightly copies and Hetzner's backups hold the database; there is no off-site copy
  outside Hetzner (audit M6 suggests an encrypted copy to a private store as a next step).
- SSH accepts root's password (the owner logs in with it); fail2ban bans repeated failures.
  With an SSH key on the server, password logins can be switched off.
- The deploy key can write to every branch of the private repository, and GitHub's branch
  protection isn't available for private repositories on a free plan: a compromised server
  could rewrite `main`. Every clone (the owner's, the old public repository) still holds
  the history.
