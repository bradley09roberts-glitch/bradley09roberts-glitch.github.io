"""The deployment kit (deploy/, docs/server.md) stays consistent: the pasted command in the
owner's instructions is deploy/bootstrap.sh, every unit runs a script that exists, every timer
has its service and is known to `footy doctor`, and the pinned GitHub host key matches its
published fingerprint. Static checks only; deploy/test-jobs.sh runs the jobs themselves."""

import base64
import hashlib
import io
import lzma
import os
import re
import subprocess
import tarfile

from footy.config import PROJECT_ROOT
from footy.doctor import TIMER_MAX_AGE

DEPLOY = PROJECT_ROOT / "deploy"
SYSTEMD = DEPLOY / "systemd"


def test_the_pasted_command_is_bootstrap_sh():
    doc = (PROJECT_ROOT / "docs" / "server.md").read_text()
    block = re.search(r"<<'FOOTY_BOOTSTRAP'\n(.*?)\nFOOTY_BOOTSTRAP\n", doc, re.S)
    assert block, "docs/server.md has no FOOTY_BOOTSTRAP block"
    assert block.group(1) + "\n" == (DEPLOY / "bootstrap.sh").read_text()


def test_units_run_scripts_that_exist():
    for unit in SYSTEMD.glob("*.service"):
        for line in unit.read_text().splitlines():
            if line.startswith("ExecStart=@PROJECT@/"):
                script = PROJECT_ROOT / line.removeprefix("ExecStart=@PROJECT@/").split()[0]
                assert script.is_file(), f"{unit.name}: {script} is missing"
                assert os.access(script, os.X_OK), f"{script} isn't executable"


def test_every_timer_has_its_service_and_doctor_knows_it():
    timers = {t.stem.removeprefix("footy-") for t in SYSTEMD.glob("footy-*.timer")}
    services = {s.stem.removeprefix("footy-") for s in SYSTEMD.glob("footy-*.service")}
    assert timers <= services
    assert timers == set(TIMER_MAX_AGE)
    for job in timers:
        text = (SYSTEMD / f"footy-{job}.service").read_text()
        assert "OnFailure=footy-failure@%n.service" in text
        assert "Europe/London" in (SYSTEMD / f"footy-{job}.timer").read_text() or job == (
            "capture-odds"
        )


def test_pinned_github_host_key():
    keys = set()
    for name in ("bootstrap.sh", "setup.sh"):
        text = (DEPLOY / name).read_text()
        keys |= set(re.findall(r"GITHUB_KEY='github.com ssh-ed25519 (\S+)'", text))
    assert len(keys) == 1, "bootstrap.sh and setup.sh pin different keys"
    blob = base64.b64decode(keys.pop())
    fp = "SHA256:" + base64.b64encode(hashlib.sha256(blob).digest()).decode().rstrip("=")
    assert fp == "SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU"  # docs.github.com
    assert f"GITHUB_FP='{fp}'" in (DEPLOY / "setup.sh").read_text()


def test_caddy_template_keeps_everything_behind_the_password():
    text = (DEPLOY / "Caddyfile.template").read_text()
    site = text[text.index("@HOST@ {") :]
    assert site.index("basic_auth") < site.index("handle")  # applies to every route
    assert "reverse_proxy 127.0.0.1:8000" in site
    assert "@AUTH_HASH@" in site and "@AUTH_USER@" in site


# --- the bootstrap archive (deploy/make-bootstrap-archive.sh) -------------------------------

ARCHIVE_DIRS = ("backtests", "backtests_euro", "reports", "lab", "europe")


def _archive_data(tmp_path, europe: dict[str, str], ledger_header="model,match_id,p_H,p_D,p_A"):
    data = tmp_path / "data"
    for d in ARCHIVE_DIRS:
        (data / d).mkdir(parents=True)
    run = data / "backtests" / "m__validation__2324"
    run.mkdir()
    (run / "ledger.csv").write_text(f"{ledger_header}\nm,1,0.5,0.3,0.2\n")
    for name, text in europe.items():
        (data / "europe" / name).write_text(text)
    return data


def _make_archive(data, out):
    return subprocess.run(
        ["bash", str(DEPLOY / "make-bootstrap-archive.sh"), str(out)],
        env={**os.environ, "FOOTY_DATA_DIR": str(data)},
        capture_output=True,
        text=True,
        timeout=300,
    )


def _archived_names(out) -> list[str]:
    parts = sorted(out.glob("bootstrap-data.tar.xz.part-*"))
    blob = b"".join(p.read_bytes() for p in parts)
    with tarfile.open(fileobj=io.BytesIO(lzma.decompress(blob))) as tar:
        return [m.name for m in tar.getmembers() if m.isfile()]


UEFA_CSV = "competition,season,home_name,away_name,source\nUCL,2627,A,B,wikipedia\n"
UEFA_COM = "competition,wiki_home,uefa_home,uefa_kickoff_utc,uefa_raw_file_id\nUCL,A,A,x,7\n"


def test_archive_leaves_out_the_old_uefa_com_crosscheck(tmp_path):
    # D-023: UEFA.com content is never committed; data/ may still hold the crosscheck file
    # written before 22 Oct (docs/europe/uefa-2627.md)
    data = _archive_data(
        tmp_path, {"uefa_2627.csv": UEFA_CSV, "uefa_2627_crosscheck.csv": UEFA_COM}
    )
    r = _make_archive(data, tmp_path / "out")
    assert r.returncode == 0, r.stderr
    names = _archived_names(tmp_path / "out")
    assert "europe/uefa_2627.csv" in names
    assert not any("crosscheck" in n for n in names)
    assert "uefa_2627_crosscheck" not in (tmp_path / "out" / "FILES.sha256").read_text()


def test_archive_refuses_uefa_com_content(tmp_path):
    data = _archive_data(tmp_path, {"uefa_2627.csv": UEFA_CSV, "venues.csv": UEFA_COM})
    r = _make_archive(data, tmp_path / "out")
    assert r.returncode == 1
    assert "UEFA.com" in r.stderr and "venues.csv" in r.stderr
    assert not list((tmp_path / "out").glob("bootstrap-data.tar.xz.part-*"))


def test_archive_refuses_price_columns(tmp_path):
    data = _archive_data(
        tmp_path, {"uefa_2627.csv": UEFA_CSV}, ledger_header="model,match_id,p_H,p_D,p_A,B365H"
    )
    r = _make_archive(data, tmp_path / "out")
    assert r.returncode == 1 and "B365H" in r.stderr


# --- a new server's first minutes: apt is often busy with automatic updates -----------------


def test_apt_waits_for_the_first_boot_updates():
    # `apt-get update` fails at once while another apt holds its lock (no timeout option
    # helps); `apt-get install` waits only with DPkg::Lock::Timeout
    setup = (DEPLOY / "setup.sh").read_text().splitlines()
    calls = [line for line in setup if re.search(r"(^|[;&|(]\s*)apt-get\s", line.strip())]
    assert calls and all("apt_get()" in c or "DPkg::Lock::Timeout" in c for c in calls), calls
    assert not [line for line in setup if re.match(r"\s*apt-get ", line)]
    boot = (DEPLOY / "bootstrap.sh").read_text()
    for line in boot.splitlines():
        if "apt-get" in line and "install" in line:
            assert "DPkg::Lock::Timeout" in line, line
        if "apt-get" in line and "update" in line:
            assert line.lstrip().startswith(("until", "while")), line
