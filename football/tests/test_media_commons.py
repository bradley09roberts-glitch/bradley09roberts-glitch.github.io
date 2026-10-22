"""The Wikimedia Commons downloader, against httpx.MockTransport (no network)."""

import json

import httpx
import pytest

from footy.europe.wiki_fetch import USER_AGENT
from footy.media import commons
from footy.media.commons import CommonsClient, licence_is_free, plain_text, run_download

PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 64
JPEG = b"\xff\xd8\xff\xe0" + b"\0" * 64

FACTS = {
    "eng-arsenal": {
        "qid": "Q9617",
        "crest_file": "Arsenal FC.svg",
        "venue": {"qid": "Q8681", "name": "Emirates Stadium", "image_file": "Emirates.jpg"},
        "colours": [],
    },
    "eng-man-city": {
        "qid": "Q50602",
        "crest_file": None,  # no P154: nothing to fetch for the crest
        "venue": {"qid": "Q1", "name": "City of Manchester Stadium", "image_file": "Etihad.jpg"},
        "colours": [],
    },
}


def imageinfo(title, *, licence="CC BY-SA 4.0", code="cc-by-sa-4.0", mime="image/png"):
    ext = {"image/png": "png", "image/jpeg": "jpg"}[mime]
    return {
        "query": {
            "pages": [
                {
                    "title": title,
                    "imageinfo": [
                        {
                            "url": f"https://upload.wikimedia.org/orig/{ext}",
                            "descriptionurl": "https://commons.wikimedia.org/wiki/" + title,
                            "thumburl": f"https://upload.wikimedia.org/thumb/{title}.{ext}",
                            "thumbwidth": 128,
                            "thumbheight": 120,
                            "thumbmime": mime,
                            "mime": "image/svg+xml",
                            "extmetadata": {
                                "LicenseShortName": {"value": licence},
                                "License": {"value": code},
                                "Artist": {"value": '<a href="//x">Jane <b>Doe</b></a>'},
                                "Credit": {"value": "Own work"},
                                "AttributionRequired": {"value": "true"},
                                "UsageTerms": {"value": "Creative Commons"},
                            },
                        }
                    ],
                }
            ]
        }
    }


class FakeCommons:
    """Answers the Commons API and upload.wikimedia.org; `licences` overrides per file."""

    def __init__(self, licences=None, fail_on=None):
        self.licences = licences or {}
        self.fail_on = fail_on  # (substring, response) returned for a matching URL
        self.requests: list[httpx.Request] = []
        self.sleeps: list[float] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        url = str(request.url)
        if self.fail_on and self.fail_on[0] in url:
            return self.fail_on[1]
        if request.url.host == "commons.wikimedia.org":
            title = request.url.params["titles"]
            name = title.removeprefix("File:")
            licence, code = self.licences.get(name, ("CC BY-SA 4.0", "cc-by-sa-4.0"))
            mime = "image/jpeg" if name.endswith(".jpg") else "image/png"
            return httpx.Response(200, json=imageinfo(title, licence=licence, code=code, mime=mime))
        if url.endswith(".png"):
            return httpx.Response(200, content=PNG, headers={"Content-Type": "image/png"})
        return httpx.Response(200, content=JPEG, headers={"Content-Type": "image/jpeg"})

    def client(self) -> CommonsClient:
        http = httpx.Client(
            transport=httpx.MockTransport(self.handler), headers={"User-Agent": USER_AGENT}
        )
        return CommonsClient(min_interval=1.0, client=http, sleep=self.sleeps.append)


def test_downloads_free_pictures_with_manifest(tmp_path):
    fake = FakeCommons()
    report = run_download(FACTS, tmp_path, fake.client())
    assert report["status"] == "complete"
    assert report["counts"]["downloaded"] == 3  # Arsenal crest + venue, City venue
    team = tmp_path / "eng-arsenal"
    assert (team / "crest.png").read_bytes() == PNG
    assert (team / "venue.jpg").read_bytes() == JPEG
    assert not (tmp_path / "eng-man-city" / "crest.png").exists()
    manifest = json.loads((team / "manifest.json").read_text())
    crest = manifest["files"]["crest"]
    assert crest["licence"] == "CC BY-SA 4.0"
    assert crest["author"] == "Jane Doe"  # HTML stripped
    assert crest["attribution_required"] is True
    assert crest["source_url"] == "https://commons.wikimedia.org/wiki/File:Arsenal FC.svg"
    assert crest["attribution"] == "Arsenal FC.svg by Jane Doe, CC BY-SA 4.0, via Wikimedia Commons"
    # Every request identifies itself; the API asks for a ~128 px crest and ~640 px venue.
    assert all(r.headers["User-Agent"] == USER_AGENT for r in fake.requests)
    widths = [r.url.params.get("iiurlwidth") for r in fake.requests if "api.php" in str(r.url)]
    assert widths == ["128", "640", "640"]
    # One request per second: every request after the first waited (sleep recorded).
    assert len(fake.sleeps) >= len(fake.requests) - 1
    assert all(0 < s <= 1.0 for s in fake.sleeps)


def test_resumable_second_run_makes_no_requests(tmp_path):
    run_download(FACTS, tmp_path, FakeCommons().client())
    again = FakeCommons()
    report = run_download(FACTS, tmp_path, again.client())
    assert again.requests == []
    assert report["counts"]["skipped"] == 3


def test_non_free_licence_refused_and_logged(tmp_path):
    fake = FakeCommons(licences={"Emirates.jpg": ("CC BY-NC 2.0", "cc-by-nc-2.0")})
    report = run_download({"eng-arsenal": FACTS["eng-arsenal"]}, tmp_path, fake.client())
    assert report["counts"] == {
        "downloaded": 1,
        "skipped": 0,
        "refused": 1,
        "missing": 0,
        "error": 0,
    }
    team = tmp_path / "eng-arsenal"
    assert not (team / "venue.jpg").exists()
    manifest = json.loads((team / "manifest.json").read_text())
    assert manifest["refused"][0]["commons_file"] == "Emirates.jpg"
    assert manifest["refused"][0]["licence"] == "CC BY-NC 2.0"
    # The refused file's image was never requested.
    assert not any("Emirates.jpg.jpg" in str(r.url) for r in fake.requests)
    # A rerun doesn't ask again.
    again = FakeCommons()
    run_download({"eng-arsenal": FACTS["eng-arsenal"]}, tmp_path, again.client())
    assert again.requests == []


def test_first_429_stops_the_whole_run_and_blocks_until_retry_after(tmp_path):
    fake = FakeCommons(fail_on=("Emirates", httpx.Response(429, headers={"Retry-After": "3600"})))
    report = run_download(FACTS, tmp_path, fake.client())
    assert report["status"] == "stopped_http_429"
    assert report["stopped_at"]["team_key"] == "eng-arsenal"
    assert report["teams_finished"] == 0
    assert report["retry_after"] == 3600
    # Stopped at once: the crest (2 requests) and the one venue request, nothing for City.
    assert len(fake.requests) == 3
    assert not any("Etihad" in str(r.url) for r in fake.requests)
    # The crest already fetched is kept, so the next run resumes after the block.
    assert (tmp_path / "eng-arsenal" / "crest.png").exists()
    blocked = FakeCommons()
    refused = run_download(FACTS, tmp_path, blocked.client())
    assert refused["status"] == "refused"
    assert blocked.requests == []


def test_429_without_retry_after_still_stops_and_next_run_may_start(tmp_path):
    fake = FakeCommons(fail_on=("api.php", httpx.Response(429)))
    report = run_download(FACTS, tmp_path, fake.client())
    assert report["status"] == "stopped_http_429"
    assert len(fake.requests) == 1  # never retried
    assert commons.blocked_until(tmp_path) is None


def test_403_stops_the_run_too(tmp_path):
    fake = FakeCommons(fail_on=("upload.wikimedia.org", httpx.Response(403)))
    report = run_download(FACTS, tmp_path, fake.client())
    assert report["status"] == "stopped_http_403"
    assert len(fake.requests) == 2  # the first file's API request, then its image
    assert report["teams_finished"] == 0


def test_503_with_short_retry_after_is_waited_out_once(tmp_path):
    responses = [
        httpx.Response(503, headers={"Retry-After": "7"}),
        httpx.Response(200, json={"query": {"pages": []}}),
    ]
    sleeps: list[float] = []
    http = httpx.Client(transport=httpx.MockTransport(lambda r: responses.pop(0)))
    client = CommonsClient(min_interval=0.0, client=http, sleep=sleeps.append)
    assert client.get(commons.COMMONS_API).status_code == 200
    assert 7 in sleeps


def test_missing_file_reported_not_fatal(tmp_path):
    def handler(request):
        return httpx.Response(200, json={"query": {"pages": [{"title": "x", "missing": True}]}})

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = CommonsClient(min_interval=0.0, client=http, sleep=lambda s: None)
    report = run_download({"eng-arsenal": FACTS["eng-arsenal"]}, tmp_path, client)
    assert report["status"] == "complete"
    assert report["counts"]["missing"] == 2


def test_probe_reports_block():
    http = httpx.Client(
        transport=httpx.MockTransport(lambda r: httpx.Response(429, headers={"Retry-After": "5"}))
    )
    client = CommonsClient(min_interval=0.0, client=http, sleep=lambda s: None)
    assert commons.probe(client) == {
        "status": 429,
        "ok": False,
        "rate_limited": True,
        "retry_after": 5.0,
    }
    for status, ok in [(200, True), (403, False)]:
        http = httpx.Client(
            transport=httpx.MockTransport(lambda r, s=status: httpx.Response(s, json={}))
        )
        client = CommonsClient(min_interval=0.0, client=http, sleep=lambda s: None)
        assert commons.probe(client) == {"status": status, "ok": ok, "rate_limited": False}


@pytest.mark.parametrize(
    "short,code,free",
    [
        ("CC BY-SA 4.0", "cc-by-sa-4.0", True),
        ("CC BY-SA 3.0 de", None, True),
        ("CC BY 2.0", None, True),
        ("CC BY-SA 2.5,2.0,1.0", None, True),
        ("CC0", "cc0", True),
        ("Public domain", "pd", True),
        (None, "pd", True),
        ("CC BY-NC 2.0", "cc-by-nc-2.0", False),
        ("CC BY-ND 4.0", None, False),
        # The two fields disagree: NC or ND in either refuses the file.
        ("CC BY-NC-SA 4.0", "cc-by-sa-4.0", False),
        ("CC BY-SA 4.0", "cc-by-nd-4.0", False),
        ("GFDL", "gfdl", False),
        ("Copyrighted free use", None, False),
        ("Fair use", None, False),
        (None, None, False),
    ],
)
def test_licence_is_free(short, code, free):
    assert licence_is_free(short, code) is free


def test_plain_text():
    assert plain_text('<a href="x">Ann &amp; <i>Bob</i></a>\n ') == "Ann & Bob"
    assert plain_text("") is None
    assert plain_text("x" * 400).endswith("…")
