#!/usr/bin/env python3
"""Build sanitized HTTP evidence artifacts from an OWASP ZAP scan.

ZAP Automation Framework can only export the raw message log (HAR / URL list) and
the Sites Tree (YAML).  It cannot emit per-request CSVs or a coverage summary, so
this script post-processes those exports on the runner and produces the audit
artifacts required by the Technical Audit Report.

Design rules (see docs/TECHNICAL_AUDIT_REPORT.md):

* The HAR is treated as *temporary runner-only data*: it is read here and never
  copied into the evidence directory.  Header values (Authorization / Cookie),
  request bodies and response bodies are never read into the output at all.
* URLs are sanitized: userinfo and fragments are dropped, every query value is
  replaced with ``<redacted>`` (parameter names are kept for traceability) and
  token-looking path segments are redacted.
* ``--validate-only`` is meant to run immediately before ``actions/upload-artifact``
  and fails when a credential pattern leaks or when a raw HAR would be uploaded.

Outputs (in ``--out-dir``):

1. ``http_evidence.jsonl``      sanitized per-request evidence (method/status/URL/...)
2. ``http_status_detail.csv``   one row per request
3. ``http_status_summary.csv``  aggregated HTTP status codes
4. ``http_coverage.json``       totals, unique URLs, status/method distributions
5. ``sites_tree.yaml``          sanitized ZAP Sites Tree

Usage:
    python3 zap_http_evidence.py \\
        --har zap_workspace/http_raw.har \\
        --sites-tree zap_workspace/reports/sites_tree_raw.yaml \\
        --report-dir zap_workspace/reports \\
        --out-dir zap_workspace/evidence \\
        --target-url "$APP_ROOT" --run-id "$GITHUB_RUN_ID" --commit "$GITHUB_SHA"

    python3 zap_http_evidence.py --validate-only \\
        --out-dir zap_workspace/evidence \\
        --report-dir zap_workspace/reports \\
        --har-free-root zap_workspace
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

SCHEMA = "eoffice.zap-http-coverage/1"
REDACTED = "<redacted>"

EVIDENCE_JSONL = "http_evidence.jsonl"
DETAIL_CSV = "http_status_detail.csv"
SUMMARY_CSV = "http_status_summary.csv"
COVERAGE_JSON = "http_coverage.json"
SITES_TREE_YAML = "sites_tree.yaml"

# Patterns that must never appear in an uploaded evidence artifact.
STRICT_SECRET_PATTERNS = [
    ("jwt", re.compile(r"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}")),
    ("bearer-token", re.compile(r"(?i)\bbearer\s+[A-Za-z0-9._\-]{8,}")),
    ("basic-auth", re.compile(r"(?i)\bbasic\s+[A-Za-z0-9+/=]{12,}")),
    ("cookie-header", re.compile(r"(?i)\b(set-)?cookie\s*:")),
    # Query parameter names are kept for traceability, so a name followed by the
    # sanitizer placeholder is not a leak (any other value is).
    ("credential-field", re.compile(r"(?i)\b(access_?token|refresh_?token|password|passwd|client_?secret|api_?key)\b\s*[:=]\s*(?!<redacted>)[^\s,;\"]+")),
    ("private-key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("aws-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b")),
]

# Report templates legitimately mention words such as "Cookie" in alert titles, so
# reports are only scanned for high-signal secrets (an actually leaked credential).
HIGH_SIGNAL_SECRET_PATTERNS = [
    pair for pair in STRICT_SECRET_PATTERNS
    if pair[0] in {"jwt", "bearer-token", "basic-auth", "private-key", "aws-key", "github-token"}
]

TOKEN_PATH_SEGMENT = re.compile(r"^[A-Za-z0-9_-]{24,}$")
JWT_PATH_SEGMENT = re.compile(r"^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$")
QUERY_IN_TEXT = re.compile(r"\?([^\s'\"<>]+)")


def sanitize_url(raw_url: str) -> str:
    """Return a URL safe to persist: no credentials, no query values, no fragment."""
    try:
        parts = urlsplit(raw_url.strip())
    except ValueError:
        return REDACTED

    netloc = parts.netloc
    if "@" in netloc:  # strip userinfo
        netloc = netloc.rsplit("@", 1)[1]

    sanitized_segments = []
    for segment in parts.path.split("/"):
        if JWT_PATH_SEGMENT.match(segment) or TOKEN_PATH_SEGMENT.match(segment):
            sanitized_segments.append(REDACTED)
        else:
            sanitized_segments.append(segment)
    path = "/".join(sanitized_segments)

    query = ""
    if parts.query:
        pairs = parse_qsl(parts.query, keep_blank_values=True)
        query = urlencode([(key, REDACTED) for key, _ in pairs], safe="<>")

    return urlunsplit((parts.scheme, netloc, path, query, ""))


def _iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _status_class(status: int) -> str:
    if status <= 0:
        return "no-response"
    return f"{status // 100}xx"


def load_har(har_path: Path) -> list[dict]:
    with har_path.open(encoding="utf-8") as handle:
        har = json.load(handle)
    entries = har.get("log", {}).get("entries", [])
    if not isinstance(entries, list):
        raise ValueError(f"{har_path}: unexpected HAR structure (log.entries is not a list)")
    return entries


def build_records(entries: list[dict], target_url: str) -> list[dict]:
    target = urlsplit(target_url.strip())
    target_prefix = (target.path or "/").rstrip("/")
    records: list[dict] = []

    for index, entry in enumerate(entries, start=1):
        request = entry.get("request") or {}
        response = entry.get("response")
        url = sanitize_url(str(request.get("url", "")))
        status = int((response or {}).get("status") or 0)

        path = urlsplit(url).path or "/"
        in_scope = bool(target.hostname) and target.hostname in url and (
            not target_prefix or path.startswith(target_prefix)
        )

        content = (response or {}).get("content") or {}
        post_data = request.get("postData") or {}

        records.append({
            "seq": index,
            "timestamp": entry.get("startedDateTime") or "",
            "method": str(request.get("method", "")).upper(),
            "status": status,
            "status_class": _status_class(status),
            "has_response": status > 0,
            "in_scope": in_scope,
            "url": url,
            "path": path,
            "duration_ms": int(round(float(entry.get("time") or 0))),
            "response_bytes": int(content.get("size") or 0),
            "mime_type": str(content.get("mimeType", "")).split(";")[0],
            # Body length only - the body itself is never copied into evidence.
            "request_body_bytes": len(str(post_data.get("text") or "")),
        })
    return records


def _sanitize_query_text(match: re.Match) -> str:
    pairs = parse_qsl(match.group(1), keep_blank_values=True)
    if not pairs:
        return match.group(0)
    return "?" + urlencode([(key, REDACTED) for key, _ in pairs], safe="<>")


def sanitize_sites_tree(source: Path, destination: Path, target_url: str) -> int:
    """Copy the Sites Tree YAML with every URL sanitized. Returns the node count.

    Sites Tree nodes are relative (``- 10?token=...``), so sanitizing absolute URLs
    alone is not enough: query strings are redacted on every node line as well.
    """
    text = source.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"https?://[^\s'\"<>]+", lambda m: sanitize_url(m.group(0)), text)
    text = QUERY_IN_TEXT.sub(_sanitize_query_text, text)
    nodes = sum(1 for line in text.splitlines() if line.lstrip().startswith("- "))
    destination.write_text(text, encoding="utf-8")
    return nodes


def write_detail_csv(records: list[dict], path: Path) -> None:
    columns = [
        "seq", "timestamp", "method", "status", "status_class", "has_response",
        "in_scope", "duration_ms", "response_bytes", "request_body_bytes",
        "mime_type", "path", "url",
    ]
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(records)


def write_summary_csv(records: list[dict], path: Path) -> None:
    counts = Counter(record["status"] for record in records)
    total = len(records) or 1
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["status_code", "status_class", "count", "percent", "has_response"])
        for status, count in sorted(counts.items(), key=lambda item: (-item[0] if item[0] else 0)):
            writer.writerow([
                status,
                _status_class(status),
                count,
                f"{(count * 100.0 / total):.2f}",
                "true" if status > 0 else "false",
            ])


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_coverage(args, records: list[dict], har_entries: int, tree_nodes: int | None) -> Path:
    status_counts = Counter(record["status"] for record in records)
    status_class_counts = Counter(record["status_class"] for record in records)
    method_counts = Counter(record["method"] for record in records)
    unique_urls = {record["url"] for record in records}
    unique_paths = {record["path"] for record in records}
    path_counts = Counter(record["path"] for record in records)
    target = urlsplit(args.target_url or "")

    coverage = {
        "schema": SCHEMA,
        "generated_at": _iso_now(),
        "scan": {
            "run_id": args.run_id or "",
            "commit": args.commit or "",
            "workflow": args.workflow or "",
            "target_host": target.hostname or "",
            "target_base_path": target.path or "",
        },
        "source": {
            "har_entries": har_entries,
            "har_in_upload": False,
            "headers_persisted": False,
            "bodies_persisted": False,
            "query_values": REDACTED,
            "sites_tree_nodes": tree_nodes,
        },
        "totals": {
            "requests": len(records),
            "unique_urls": len(unique_urls),
            "unique_paths": len(unique_paths),
            "requests_with_response": sum(1 for record in records if record["has_response"]),
            "requests_without_response": sum(1 for record in records if not record["has_response"]),
            "requests_in_scope": sum(1 for record in records if record["in_scope"]),
            "requests_out_of_scope": sum(1 for record in records if not record["in_scope"]),
        },
        "status_codes": {str(code): count for code, count in sorted(status_counts.items())},
        "status_classes": dict(sorted(status_class_counts.items())),
        "methods": dict(sorted(method_counts.items())),
        "top_paths_by_requests": [
            {"path": path, "requests": count}
            for path, count in path_counts.most_common(15)
        ],
        "artifacts": {},
    }

    out_dir: Path = args.out_dir
    for name in (EVIDENCE_JSONL, DETAIL_CSV, SUMMARY_CSV, SITES_TREE_YAML):
        candidate = out_dir / name
        if candidate.exists():
            coverage["artifacts"][name] = {
                "sha256": sha256_of(candidate),
                "bytes": candidate.stat().st_size,
            }

    coverage_path = out_dir / COVERAGE_JSON
    coverage_path.write_text(json.dumps(coverage, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return coverage_path


def write_evidence_jsonl(records: list[dict], path: Path) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def redact_reports(report_dir: Path) -> int:
    """Redact high-signal secrets inside ZAP report files (they can embed evidence)."""
    if not report_dir or not report_dir.exists():
        return 0
    redactions = 0
    for path in sorted(report_dir.glob("zap_report.*")):
        text = path.read_text(encoding="utf-8", errors="replace")
        original = text
        for _, pattern in HIGH_SIGNAL_SECRET_PATTERNS:
            text, hits = pattern.subn(f"[REDACTED-BY-{SCHEMA.split('/')[0]}]", text)
            redactions += hits
        if text != original:
            path.write_text(text, encoding="utf-8")
            print(f"[redact] {path.name}: scrubbed {redactions} secret-pattern hit(s)")
    return redactions


def scan_file(path: Path, patterns) -> list[str]:
    findings = []
    text = path.read_text(encoding="utf-8", errors="replace")
    for name, pattern in patterns:
        for match in pattern.finditer(text):
            excerpt = match.group(0)[:24]
            findings.append(f"{path.name}: {name} -> {excerpt!r}")
    return findings


def validate(*, out_dir: Path, report_dir: Path | None, har_free_root: Path | None) -> int:
    errors: list[str] = []

    if har_free_root and har_free_root.exists():
        for har in har_free_root.rglob("*.har"):
            errors.append(f"raw HAR present in upload scope: {har}")

    if not out_dir.exists():
        errors.append(f"evidence directory missing: {out_dir}")
    else:
        for required in (EVIDENCE_JSONL, DETAIL_CSV, SUMMARY_CSV, COVERAGE_JSON):
            candidate = out_dir / required
            if not candidate.exists() or candidate.stat().st_size == 0:
                errors.append(f"required evidence file missing/empty: {required}")
        for path in sorted(out_dir.iterdir()):
            if path.suffix.lower() == ".har":
                errors.append(f"raw HAR inside evidence directory: {path.name}")
                continue
            if path.suffix.lower() in {".jsonl", ".csv", ".json", ".yaml", ".yml"}:
                for finding in scan_file(path, STRICT_SECRET_PATTERNS):
                    errors.append(f"credential pattern in evidence: {finding}")
                if path.name == SITES_TREE_YAML:
                    for raw_query in QUERY_IN_TEXT.finditer(path.read_text(encoding="utf-8", errors="replace")):
                        if any(value and value != REDACTED
                               for _, value in parse_qsl(raw_query.group(1), keep_blank_values=True)):
                            errors.append(f"unredacted query value in evidence: {path.name} -> {raw_query.group(0)[:40]!r}")

    if report_dir and report_dir.exists():
        for path in sorted(report_dir.glob("zap_report.*")):
            for finding in scan_file(path, HIGH_SIGNAL_SECRET_PATTERNS):
                errors.append(f"credential pattern in report: {finding}")
        for path in sorted(report_dir.rglob("*.har")):
            errors.append(f"raw HAR inside report directory: {path}")

    if errors:
        print("[FAIL] artifact validation:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print("[PASS] artifact validation: no credentials, no raw HAR, all required files present")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--har", type=Path, help="raw ZAP HAR export (runner only, never uploaded)")
    parser.add_argument("--sites-tree", type=Path, help="raw ZAP Sites Tree YAML export")
    parser.add_argument("--report-dir", type=Path, help="directory holding zap_report.* files")
    parser.add_argument("--out-dir", type=Path, required=True, help="sanitized evidence output directory")
    parser.add_argument("--target-url", default="", help="staging base URL, used to flag in-scope requests")
    parser.add_argument("--run-id", default="", help="GitHub Actions run id")
    parser.add_argument("--commit", default="", help="commit SHA under test")
    parser.add_argument("--workflow", default="", help="workflow name/ref")
    parser.add_argument("--validate-only", action="store_true", help="only validate existing artifacts")
    parser.add_argument("--har-free-root", type=Path, help="directory tree that must not contain any .har")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    if args.validate_only:
        return validate(out_dir=args.out_dir, report_dir=args.report_dir, har_free_root=args.har_free_root)

    if not args.har or not args.har.exists():
        print(f"[FAIL] raw HAR export not found: {args.har}. "
              "ZAP Automation Framework 'export' job (type: har) did not produce the file.")
        return 1

    redactions = redact_reports(args.report_dir) if args.report_dir else 0
    args.out_dir.mkdir(parents=True, exist_ok=True)

    entries = load_har(args.har)
    records = build_records(entries, args.target_url)

    write_evidence_jsonl(records, args.out_dir / EVIDENCE_JSONL)
    write_detail_csv(records, args.out_dir / DETAIL_CSV)
    write_summary_csv(records, args.out_dir / SUMMARY_CSV)

    tree_nodes = None
    if args.sites_tree and args.sites_tree.exists():
        tree_nodes = sanitize_sites_tree(args.sites_tree, args.out_dir / SITES_TREE_YAML, args.target_url)
    elif args.sites_tree:
        print(f"[warn] sites tree export not found: {args.sites_tree}")

    coverage_path = write_coverage(args, records, len(entries), tree_nodes)

    print(f"[info] HAR entries: {len(entries)}; evidence rows: {len(records)}; "
          f"unique URLs: {len({r['url'] for r in records})}; report redactions: {redactions}")
    print(f"[info] coverage: {coverage_path}")

    return validate(out_dir=args.out_dir, report_dir=args.report_dir, har_free_root=args.har_free_root)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
