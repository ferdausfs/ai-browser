#!/usr/bin/env python3
"""
Generates app/src/main/assets/tracker_domains.txt for the AI Browser project.

Source: StevenBlack/hosts (MIT licensed) - the unified "hosts" variant base file.
https://github.com/StevenBlack/hosts  (license: MIT)

The script downloads the raw hosts file, extracts pure hostnames (ignoring
localhost/loopback/broadcast entries and comments), dedupes, lowercases and
validates them, then writes them sorted, one per line, with a provenance
header. Run again (and commit the result) to refresh the list.

Usage: python3 gen_tracker_list.py [output_path]
"""
import re
import sys
import urllib.request
from datetime import datetime, timezone

SOURCE_URLS = [
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
]
# Fallback mirror if the first is unreachable.
FALLBACK_URLS = [
    "https://cdn.jsdelivr.net/gh/StevenBlack/hosts@master/hosts",
]

HOST_RE = re.compile(r"^(?:0\.0\.0\.0|127\.0\.0\.1|::1|febf::1)\s+(\S+)", re.IGNORECASE)
DOMAIN_RE = re.compile(
    r"^(?=.{1,253}\.?$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+"
    r"[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$"
)

# Entries that must never be blocked (breaks device basics / self-update).
ALLOWLIST = {
    "localhost", "localhost.localdomain", "local", "broadcasthost",
    "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
    "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    "github.com", "api.github.com", "objects.githubusercontent.com",
    "raw.githubusercontent.com", "release-assets.githubusercontent.com",
}


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "aegis-browser-listgen/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", errors="replace")


def main() -> int:
    out_path = sys.argv[1] if len(sys.argv) > 1 else (
        "/home/z/my-project/ai-browser/app/src/main/assets/tracker_domains.txt"
    )
    body = None
    used = None
    for url in SOURCE_URLS + FALLBACK_URLS:
        try:
            body = fetch(url)
            used = url
            break
        except Exception as e:  # noqa: BLE001
            print(f"! fetch failed for {url}: {e}")
    if body is None:
        print("ERROR: could not download any source list")
        return 1

    domains = set()
    skipped = 0
    for raw in body.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = HOST_RE.match(line)
        if not m:
            skipped += 1
            continue
        host = m.group(1).strip().lower().rstrip(".")
        if not host or host in ALLOWLIST:
            continue
        # Drop artifacts of hosts files that repeat the sinkhole address inside
        # the hostname itself (e.g. "0.0.0.0.creative.example.com").
        if host.startswith("0.0.0.0.") or host.startswith("127.0.0.1.") or host.startswith("::1."):
            continue
        if DOMAIN_RE.match(host):
            domains.add(host)
        else:
            skipped += 1

    domains = sorted(domains)
    header = (
        "# Tracker/ad/malware domain list for Aegis Browser request blocking.\n"
        f"# Source: StevenBlack/hosts unified base file ({used})\n"
        "# License: MIT (c) Steven Black - https://github.com/StevenBlack/hosts\n"
        f"# Generated: {datetime.now(timezone.utc).isoformat(timespec='seconds')}\n"
        f"# Entries: {len(domains)}\n"
        "# Scope note: baseline blocklist; NOT comprehensive anti-tracking.\n"
    )
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(domains))
        f.write("\n")

    print(f"OK wrote {len(domains)} domains to {out_path} (skipped {skipped} malformed lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
