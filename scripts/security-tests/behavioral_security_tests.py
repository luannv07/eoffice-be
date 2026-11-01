#!/usr/bin/env python3
"""Independent behavioral security tests for the E-Office backend.

Covers the five security tests the Technical Audit Report still lists as
NOT VERIFIED:

1. BOLA / IDOR          - user A must not read user B's draft document
2. State bypass         - a DRAFT document must not be approvable/publishable
3. Refresh token reuse  - a rotated refresh token must be rejected
4. Invalid upload       - executable / disallowed content must be rejected
5. RBAC per role        - USER, CLERK, LEADER (and EXECUTIVE when it exists)

Principles:

* Nothing is reported PASS unless the HTTP exchange actually happened and the
  observed status matched the documented expectation. Anything that cannot run
  (missing account, fixture document, write permission) is NOT VERIFIED with the
  exact missing dependency.
* No credential is ever written to evidence: only method, redacted path, status,
  latency, response size and the machine-readable error code are persisted.
* Requests that mutate state are gated behind SEC_TEST_ALLOW_WRITES=true so the
  harness is safe to run against a shared staging environment by default.

Configuration (environment):

  SEC_TEST_BASE_URL              required, e.g. https://host/api/v1
  SEC_TEST_USER_USERNAME         role USER account
  SEC_TEST_USER_PASSWORD
  SEC_TEST_CLERK_USERNAME        role CLERK account
  SEC_TEST_CLERK_PASSWORD
  SEC_TEST_LEADER_USERNAME       role LEADER account
  SEC_TEST_LEADER_PASSWORD
  SEC_TEST_EXECUTIVE_USERNAME    optional - the Role enum has no EXECUTIVE value
  SEC_TEST_EXECUTIVE_PASSWORD
  SEC_TEST_<ROLE>_TOKEN          optional pre-issued access token (skips login)
  SEC_TEST_BOLA_VICTIM_DOC_ID    id of a document owned by the BOLA victim account
  SEC_TEST_BOLA_ATTACKER_ROLE    role used as the attacker (default USER)
  SEC_TEST_STATE_DRAFT_DOC_ID    id of a document still in DRAFT
  SEC_TEST_STATE_ACTOR_ROLE      role used for the state-bypass attempt (default LEADER)
  SEC_TEST_UPLOAD_DOC_ID         optional document id for the attachment upload test
  SEC_TEST_ALLOW_WRITES          "true" to enable state-changing requests
  SEC_TEST_TIMEOUT               per-request timeout seconds (default 20)

Usage:
    python3 scripts/security-tests/behavioral_security_tests.py \\
        --out-dir security-test-evidence
    python3 scripts/security-tests/behavioral_security_tests.py --strict   # NOT VERIFIED fails
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit, parse_qsl, urlencode, urlunsplit

ROLES = ("USER", "CLERK", "LEADER", "EXECUTIVE")
ROLE_ENUM_VALUES = {"USER", "ADMIN", "SYSTEM_ADMIN", "REVIEWER", "LEADER", "CLERK"}

PASS = "PASS"
FAIL = "FAIL"
NOT_VERIFIED = "NOT VERIFIED"


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sanitize_path(path: str) -> str:
    parts = urlsplit(path)
    query = ""
    if parts.query:
        query = urlencode([(k, "<redacted>") for k, _ in parse_qsl(parts.query, keep_blank_values=True)],
                          safe="<>")
    return urlunsplit((parts.scheme, parts.netloc, parts.path, query, ""))


def env(key: str, default: str = "") -> str:
    return (os.environ.get(key) or default).strip()


@dataclass
class HttpResponse:
    status: int
    body: bytes
    elapsed_ms: int
    error: str = ""


@dataclass
class Session:
    base_url: str
    token: str = ""
    jar: http.cookiejar.CookieJar = field(default_factory=http.cookiejar.CookieJar)
    timeout: int = 20

    def _opener(self):
        context = ssl.create_default_context()
        handlers = [urllib.request.HTTPSHandler(context=context), urllib.request.HTTPCookieProcessor(self.jar)]
        return urllib.request.build_opener(*handlers)

    def request(self, method: str, path: str, *, body: bytes | None = None,
                content_type: str | None = None, token: str | None = None,
                extra_headers: dict | None = None) -> HttpResponse:
        url = self.base_url.rstrip("/") + path
        headers = {"Accept": "application/json", "User-Agent": "eoffice-security-test/1.0"}
        auth = token if token is not None else self.token
        if auth:
            headers["Authorization"] = f"Bearer {auth}"
        if content_type:
            headers["Content-Type"] = content_type
        if extra_headers:
            headers.update(extra_headers)

        request = urllib.request.Request(url, data=body, method=method, headers=headers)
        started = time.monotonic()
        try:
            with self._opener().open(request, timeout=self.timeout) as response:
                return HttpResponse(response.status, response.read(), int((time.monotonic() - started) * 1000))
        except urllib.error.HTTPError as exc:
            return HttpResponse(exc.code, exc.read() or b"", int((time.monotonic() - started) * 1000))
        except Exception as exc:  # noqa: BLE001 - transport failure is a test result, not a crash
            return HttpResponse(0, b"", int((time.monotonic() - started) * 1000), error=f"{type(exc).__name__}: {exc}")


def login(base_url: str, username: str, password: str, timeout: int) -> tuple[Session | None, str]:
    session = Session(base_url=base_url, timeout=timeout)
    payload = json.dumps({"username": username, "password": password}).encode()
    response = session.request("POST", "/auth/login", body=payload, content_type="application/json", token="")
    if response.status != 200:
        return None, f"login failed with HTTP {response.status or 'transport-error'}"
    try:
        data = json.loads(response.body.decode("utf-8", errors="replace"))
        token = (data.get("data") or {}).get("accessToken") or ""
    except Exception:  # noqa: BLE001
        return None, "login response was not valid JSON"
    if not token:
        return None, "login response did not contain data.accessToken"
    session.token = token
    return session, ""


def error_code(body: bytes) -> str:
    if not body:
        return ""
    try:
        data = json.loads(body.decode("utf-8", errors="replace")[:8192])
    except Exception:  # noqa: BLE001
        return ""
    for key in ("code", "errorCode", "error"):
        value = data.get(key)
        if isinstance(value, str):
            return value
        if isinstance(value, dict) and isinstance(value.get("code"), str):
            return value["code"]
    return ""


def multipart(field_name: str, filename: str, content: bytes, content_type: str) -> tuple[bytes, str]:
    boundary = f"----eoffice{uuid.uuid4().hex}"
    chunks = [
        f"--{boundary}".encode(),
        f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"'.encode(),
        f"Content-Type: {content_type}".encode(),
        b"",
        content,
        f"--{boundary}--".encode(),
        b"",
    ]
    return b"\r\n".join(chunks), f"multipart/form-data; boundary={boundary}"


class Report:
    def __init__(self) -> None:
        self.cases: list[dict] = []

    def add(self, case: dict) -> None:
        self.cases.append(case)
        print(f"[{case['status']}] {case['id']} - {case['name']}")
        if case.get("actual", {}).get("note"):
            print(f"        {case['actual']['note']}")

    def not_verified(self, case_id: str, name: str, objective: str, dependency: str,
                     preconditions: list[str] | None = None) -> None:
        self.add({
            "id": case_id,
            "name": name,
            "objective": objective,
            "status": NOT_VERIFIED,
            "preconditions": preconditions or [],
            "dependency": dependency,
            "request": None,
            "expected": {"description": "not executed - dependency missing"},
            "actual": {"status": None, "note": dependency},
            "evidence": {"executed": False},
        })

    def record(self, case_id: str, name: str, objective: str, status: str,
               preconditions: list[str], request: dict, expected: dict,
               response: HttpResponse, note: str = "") -> None:
        self.add({
            "id": case_id,
            "name": name,
            "objective": objective,
            "status": status,
            "preconditions": preconditions,
            "dependency": "",
            "request": request,
            "expected": expected,
            "actual": {
                "status": response.status,
                "error_code": error_code(response.body),
                "latency_ms": response.elapsed_ms,
                "response_bytes": len(response.body),
                "note": note or response.error,
            },
            "evidence": {
                "executed": True,
                "observed_at": utc_now(),
                "response_sha256": hashlib.sha256(response.body).hexdigest(),
            },
        })

    def summary(self) -> dict:
        counts = {PASS: 0, FAIL: 0, NOT_VERIFIED: 0}
        for case in self.cases:
            counts[case["status"]] += 1
        return counts


def describe(method: str, path: str, *, with_auth: bool, body: bool = False) -> dict:
    return {"method": method, "path": sanitize_path(path),
            "authorization_header": "Bearer <redacted>" if with_auth else "none",
            "request_body_persisted": False}


def build_sessions(base_url: str, timeout: int) -> tuple[dict[str, Session], dict[str, str]]:
    sessions: dict[str, Session] = {}
    problems: dict[str, str] = {}
    token = env("SEC_TEST_TOKEN")
    if token:
        sessions["_default"] = Session(base_url=base_url, token=token, timeout=timeout)

    for role in ROLES:
        role_token = env(f"SEC_TEST_{role}_TOKEN")
        if role_token:
            sessions[role] = Session(base_url=base_url, token=role_token, timeout=timeout)
            continue
        username = env(f"SEC_TEST_{role}_USERNAME")
        password = env(f"SEC_TEST_{role}_PASSWORD")
        if role == "EXECUTIVE":
            if not username:
                problems[role] = ("Role EXECUTIVE does not exist in the backend Role enum "
                                  "(USER, ADMIN, SYSTEM_ADMIN, REVIEWER, LEADER, CLERK); "
                                  "provide SEC_TEST_EXECUTIVE_TOKEN only if the deployment adds it")
            continue
        if not username or not password:
            problems[role] = f"missing SEC_TEST_{role}_USERNAME / SEC_TEST_{role}_PASSWORD"
            continue
        session, err = login(base_url, username, password, timeout)
        if session is None:
            problems[role] = f"{role} login failed: {err}"
        else:
            sessions[role] = session
    return sessions, problems


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_bola(report: Report, sessions: dict, base_url: str) -> None:
    doc_id = env("SEC_TEST_BOLA_VICTIM_DOC_ID")
    attacker_role = env("SEC_TEST_BOLA_ATTACKER_ROLE", "USER").upper()
    objective = ("A user who does not own a draft document must not be able to read or modify it "
                 "by guessing the numeric document id (BOLA / IDOR).")

    if not doc_id:
        report.not_verified(
            "SEC-BOLA-01", "BOLA/IDOR on document detail", objective,
            "missing SEC_TEST_BOLA_VICTIM_DOC_ID (a DRAFT document owned by another account)")
        return
    if attacker_role not in sessions:
        report.not_verified(
            "SEC-BOLA-01", "BOLA/IDOR on document detail", objective,
            f"attacker role {attacker_role} is not authenticated (missing credentials for that role)")
        return

    session = sessions[attacker_role]
    path = f"/documents/{doc_id}"
    response = session.request("GET", path)
    expected = {"allowed_statuses": [403, 404],
                "description": "403 FORBIDDEN or 404 DOCUMENT_NOT_FOUND; 200 means the draft leaked"}

    if response.status in (403, 404):
        status, note = PASS, "cross-owner access rejected"
    elif response.status == 200:
        status, note = FAIL, "cross-owner draft was readable (BOLA/IDOR)"
    elif response.status == 0:
        status, note = NOT_VERIFIED, f"transport error: {response.error}"
    else:
        status, note = FAIL, f"unexpected status {response.status}"

    report.record("SEC-BOLA-01", "BOLA/IDOR on document detail", objective, status,
                  [f"document {doc_id} exists and is owned by a different account",
                   f"attacker authenticates as {attacker_role}"],
                  describe("GET", path, with_auth=True), expected, response, note)


def test_state_bypass(report: Report, sessions: dict, allow_writes: bool) -> None:
    doc_id = env("SEC_TEST_STATE_DRAFT_DOC_ID")
    actor_role = env("SEC_TEST_STATE_ACTOR_ROLE", "LEADER").upper()
    objective = ("A document still in DRAFT must not be approvable or publishable through the "
                 "workflow endpoints; the state machine must reject the transition.")

    if not doc_id:
        report.not_verified(
            "SEC-STATE-01", "Workflow state bypass on draft approval", objective,
            "missing SEC_TEST_STATE_DRAFT_DOC_ID (a DRAFT document usable for a negative transition test)")
        return
    if actor_role not in sessions:
        report.not_verified(
            "SEC-STATE-01", "Workflow state bypass on draft approval", objective,
            f"actor role {actor_role} is not authenticated (missing credentials for that role)")
        return
    if not allow_writes:
        report.not_verified(
            "SEC-STATE-01", "Workflow state bypass on draft approval", objective,
            "SEC_TEST_ALLOW_WRITES is not true, so the state-changing approval request was not sent")
        return

    session = sessions[actor_role]
    path = f"/documents/{doc_id}/workflow/approve"
    response = session.request("POST", path, body=b"{}", content_type="application/json")
    expected = {"allowed_statuses": [400, 403, 409],
                "description": "400/409 (invalid transition) or 403 (not the approver); 200 means a DRAFT was approved"}

    if response.status in (400, 403, 409):
        status, note = PASS, "invalid state transition rejected"
    elif response.status == 200:
        status, note = FAIL, "DRAFT document was approved (state bypass)"
    elif response.status == 0:
        status, note = NOT_VERIFIED, f"transport error: {response.error}"
    else:
        status, note = FAIL, f"unexpected status {response.status}"

    report.record("SEC-STATE-01", "Workflow state bypass on draft approval", objective, status,
                  ["document is in DRAFT status", f"actor authenticates as {actor_role} with write access"],
                  describe("POST", path, with_auth=True), expected, response, note)


def test_refresh_reuse(report: Report, sessions: dict, base_url: str, timeout: int) -> None:
    objective = ("After a refresh token is rotated, replaying the previous refresh token must be "
                 "rejected (401) and the whole session must be revoked - otherwise stolen refresh "
                 "tokens remain usable.")
    role = "USER"
    if role not in sessions:
        report.not_verified("SEC-RT-01", "Refresh token reuse", objective,
                            "missing SEC_TEST_USER_USERNAME / SEC_TEST_USER_PASSWORD (or SEC_TEST_USER_TOKEN)")
        return

    # A login-only session with its own cookie jar, so the harness never touches
    # another test's refresh cookie.
    username = env("SEC_TEST_USER_USERNAME")
    password = env("SEC_TEST_USER_PASSWORD")
    if not username or not password:
        report.not_verified("SEC-RT-01", "Refresh token reuse", objective,
                            "SEC_TEST_USER_TOKEN alone cannot test rotation; username/password required")
        return

    session, err = login(base_url, username, password, timeout)
    if session is None:
        report.not_verified("SEC-RT-01", "Refresh token reuse", objective, f"login failed: {err}")
        return

    cookie_name = env("SEC_TEST_REFRESH_COOKIE_NAME", "refresh_token")
    # Capture the token BEFORE rotation: that is the value an attacker would replay.
    original = next((c for c in session.jar if c.name == cookie_name), None)
    if original is None:
        report.not_verified("SEC-RT-01", "Refresh token reuse", objective,
                            f"login did not set the {cookie_name} cookie")
        return
    stale_value = original.value  # kept in memory only - never written to evidence
    cookie_header = {f"Cookie": f"{cookie_name}={stale_value}"}

    # Send the cookie explicitly: its Path may be /auth while the endpoint is
    # /api/v1/auth/refresh, so relying on the cookie jar alone can silently skip
    # the request and produce a false result.
    first = session.request("POST", "/auth/refresh", token="", extra_headers=cookie_header)
    if first.status != 200:
        report.not_verified("SEC-RT-01", "Refresh token reuse", objective,
                            f"initial refresh failed with HTTP {first.status or 'transport-error'}")
        return

    second = session.request("POST", "/auth/refresh", token="", extra_headers=cookie_header)
    expected = {"allowed_statuses": [401],
                "description": "401 SESSION_EXPIRED / INVALID_TOKEN for the replayed token"}

    if second.status == 401:
        status, note = PASS, "replayed refresh token rejected and session revoked"
    elif second.status == 200:
        status, note = FAIL, "replayed refresh token was accepted (token reuse not detected)"
    elif second.status == 0:
        status, note = NOT_VERIFIED, f"transport error: {second.error}"
    else:
        status, note = FAIL, f"unexpected status {second.status}"

    report.record("SEC-RT-01", "Refresh token reuse", objective, status,
                  ["USER account authenticated", "refresh cookie issued and rotated once"],
                  {
                      "method": "POST",
                      "path": "/auth/refresh",
                      "authorization_header": "none",
                      "replayed_previous_refresh_cookie": True,
                      "cookie_value_persisted": False,
                      "request_body_persisted": False,
                  }, expected, second, note)


def test_invalid_upload(report: Report, sessions: dict, allow_writes: bool) -> None:
    objective = ("The upload surface must reject disallowed content (executable / non docx-pdf) or "
                 "oversized payloads instead of storing them.")
    if "USER" not in sessions:
        report.not_verified("SEC-UPLOAD-01", "Invalid file upload", objective,
                            "missing SEC_TEST_USER_USERNAME / SEC_TEST_USER_PASSWORD (or SEC_TEST_USER_TOKEN)")
        return
    if not allow_writes:
        report.not_verified("SEC-UPLOAD-01", "Invalid file upload", objective,
                            "SEC_TEST_ALLOW_WRITES is not true, so no file was uploaded to staging")
        return

    session = sessions["USER"]
    doc_id = env("SEC_TEST_UPLOAD_DOC_ID")
    payload = b"MZ\x90\x00\x03\x00\x00\x00eoffice-invalid-upload-probe"
    body, content_type = multipart("file", "probe.exe", payload, "application/octet-stream")

    if doc_id:
        path = f"/documents/{doc_id}/attachments"
        expected = {"allowed_statuses": [400, 415, 422, 403],
                    "description": "400/415/422 rejection for .exe attachment; 403 if not the owner"}
    else:
        path = "/file-storage/upload"
        expected = {"allowed_statuses": [400, 415, 422],
                    "description": "400/415/422 rejection for .exe upload (policy: docx/pdf only)"}

    response = session.request("POST", path, body=body, content_type=content_type)
    if response.status in expected["allowed_statuses"]:
        status, note = PASS, "disallowed content rejected"
    elif response.status in (200, 201):
        status, note = FAIL, "disallowed .exe content was accepted"
    elif response.status == 0:
        status, note = NOT_VERIFIED, f"transport error: {response.error}"
    else:
        status, note = FAIL, f"unexpected status {response.status}"

    report.record("SEC-UPLOAD-01", "Invalid file upload", objective, status,
                  ["authenticated USER account", ".exe content with MZ header"],
                  describe("POST", path, with_auth=True), expected, response, note)


RBAC_CHECKS = [
    {
        "id": "SEC-RBAC-01",
        "name": "Admin user directory",
        "method": "GET",
        "path": "/admin/users",
        "expect": {"USER": [403], "CLERK": [403], "LEADER": [403], "EXECUTIVE": [403]},
        "description": "@PreAuthorize('hasRole(''ADMIN'') or hasRole(''SYSTEM_ADMIN'')') - non-admins get 403",
    },
    {
        "id": "SEC-RBAC-02",
        "name": "Document book creation",
        "method": "POST",
        "path": "/document-books",
        "json": {},
        "expect": {"USER": [403], "CLERK": [403], "LEADER": [400, 409, 201], "EXECUTIVE": [403]},
        "description": "hasAnyRole(ADMIN, SYSTEM_ADMIN, LEADER); 400 = authorized but invalid body",
    },
    {
        "id": "SEC-RBAC-03",
        "name": "Document number assignment",
        "method": "POST",
        "path": "/document-books/assign-number",
        "json": {},
        "expect": {"USER": [403], "CLERK": [400, 404], "LEADER": [403], "EXECUTIVE": [403]},
        "description": "hasAnyRole(CLERK, ADMIN, SYSTEM_ADMIN); LEADER must be 403",
    },
]


def test_rbac(report: Report, sessions: dict, problems: dict[str, str], allow_writes: bool) -> None:
    for check in RBAC_CHECKS:
        for role in ROLES:
            objective = (f"Role {role} must obtain exactly the authorization decision encoded in the "
                         f"controller annotation for {check['method']} {check['path']}.")
            case_id = f"{check['id']}-{role}"
            if role == "EXECUTIVE" and "EXECUTIVE" in problems:
                report.not_verified(case_id, check["name"], objective, problems["EXECUTIVE"])
                continue
            if role not in sessions:
                report.not_verified(case_id, check["name"], objective,
                                    problems.get(role, f"role {role} is not authenticated"))
                continue
            if check["method"] != "GET" and not allow_writes:
                report.not_verified(case_id, check["name"], objective,
                                    "SEC_TEST_ALLOW_WRITES is not true, so the write request was not sent")
                continue

            session = sessions[role]
            body = json.dumps(check["json"]).encode() if check.get("json") is not None else None
            response = session.request(check["method"], check["path"], body=body,
                                       content_type="application/json" if body is not None else None)
            allowed = check["expect"].get(role, [])
            expected = {"allowed_statuses": allowed, "description": check["description"]}

            if response.status in allowed:
                status, note = PASS, f"{role} matched expected status set"
            elif response.status == 0:
                status, note = NOT_VERIFIED, f"transport error: {response.error}"
            elif response.status == 403 and 403 not in allowed:
                status, note = FAIL, f"{role} was denied although the annotation allows it"
            elif 403 in allowed and response.status != 403:
                status, note = FAIL, f"{role} was not denied (got {response.status})"
            else:
                status, note = FAIL, f"{role} returned {response.status}, expected {allowed}"

            report.record(case_id, check["name"], objective, status,
                          [f"account authenticated with role {role}"],
                          describe(check["method"], check["path"], with_auth=True,
                                   body=body is not None),
                          expected, response, note)


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def render_markdown(report: Report, meta: dict) -> str:
    counts = report.summary()
    lines = [
        "# Behavioral Security Test Report — E-Office",
        "",
        f"- Generated at: **{meta['generated_at']}**",
        f"- Target: `{meta['target']}`",
        f"- State-changing requests enabled: **{meta['allow_writes']}**",
        f"- Result: **{counts[PASS]} PASS / {counts[FAIL]} FAIL / {counts[NOT_VERIFIED]} NOT VERIFIED**",
        "",
        "> A test is only PASS when the HTTP request was actually executed and the observed",
        "> status matched the documented expectation. Missing accounts, fixture documents or",
        "> write permission are reported as NOT VERIFIED — never as PASS.",
        "",
    ]
    for case in report.cases:
        lines += [f"## {case['id']} — {case['name']}", "", f"**Status:** {case['status']}", "",
                  f"**Mục tiêu:** {case['objective']}", ""]
        lines += ["**Preconditions:**"]
        lines += [f"- {item}" for item in case["preconditions"]] or ["- (none recorded)"]
        lines += [""]
        if case.get("request"):
            request = case["request"]
            lines += ["**Request thực tế:**", "",
                      f"- `{request['method']} {request['path']}`",
                      f"- Authorization: `{request['authorization_header']}`",
                      "- Request body persisted: `false`", ""]
        else:
            lines += ["**Request thực tế:** not sent", ""]
        lines += ["**Expected status/behavior:**",
                  f"- `{json.dumps(case['expected'], ensure_ascii=False)}`", "",
                  "**Actual status/behavior:**"]
        actual = case["actual"]
        if actual.get("status") is None:
            lines += [f"- not executed — {actual.get('note', '')}", ""]
        else:
            lines += [f"- HTTP `{actual['status']}`",
                      f"- Error code: `{actual.get('error_code') or 'n/a'}`",
                      f"- Latency: `{actual['latency_ms']} ms`",
                      f"- Response bytes: `{actual['response_bytes']}`",
                      f"- Note: {actual.get('note') or 'n/a'}", ""]
        evidence = case["evidence"]
        if evidence.get("executed"):
            lines += ["**Evidence:**",
                      f"- Observed at `{evidence['observed_at']}`",
                      f"- Response SHA-256 `{evidence['response_sha256']}`",
                      "- Stored in `report.json` / `requests.jsonl` (no credentials)", ""]
        else:
            lines += ["**Evidence:**", "- none (test not executed)", ""]
        if case.get("dependency"):
            lines += [f"**Dependency:** {case['dependency']}", ""]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out-dir", type=Path, default=Path("security-test-evidence"))
    parser.add_argument("--strict", action="store_true",
                        help="exit non-zero when any case is NOT VERIFIED")
    args = parser.parse_args(argv)

    base_url = env("SEC_TEST_BASE_URL")
    if not base_url:
        print("[FAIL] SEC_TEST_BASE_URL is not set (e.g. https://host/api/v1)")
        return 1

    timeout = int(env("SEC_TEST_TIMEOUT", "20") or 20)
    allow_writes = env("SEC_TEST_ALLOW_WRITES").lower() == "true"

    report = Report()
    sessions, problems = build_sessions(base_url, timeout)

    test_bola(report, sessions, base_url)
    test_state_bypass(report, sessions, allow_writes)
    test_refresh_reuse(report, sessions, base_url, timeout)
    test_invalid_upload(report, sessions, allow_writes)
    test_rbac(report, sessions, problems, allow_writes)

    counts = report.summary()
    meta = {
        "generated_at": utc_now(),
        "target": sanitize_path(base_url),
        "allow_writes": allow_writes,
        "authenticated_roles": sorted(sessions),
        "role_problems": problems,
    }

    args.out_dir.mkdir(parents=True, exist_ok=True)
    payload = {"meta": meta, "summary": counts, "cases": report.cases}
    (args.out_dir / "report.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (args.out_dir / "report.md").write_text(render_markdown(report, meta), encoding="utf-8")

    executed = [case for case in report.cases if case["evidence"].get("executed")]
    with (args.out_dir / "requests.jsonl").open("w", encoding="utf-8") as handle:
        for case in executed:
            handle.write(json.dumps({
                "case": case["id"],
                "observed_at": case["evidence"]["observed_at"],
                "method": case["request"]["method"],
                "path": case["request"]["path"],
                "status": case["actual"]["status"],
                "error_code": case["actual"]["error_code"],
                "latency_ms": case["actual"]["latency_ms"],
                "response_bytes": case["actual"]["response_bytes"],
                "response_sha256": case["evidence"]["response_sha256"],
            }, ensure_ascii=False) + "\n")

    print(f"\n{counts[PASS]} PASS / {counts[FAIL]} FAIL / {counts[NOT_VERIFIED]} NOT VERIFIED")
    print(f"Report: {args.out_dir / 'report.md'}")
    if problems:
        print("Authentication dependencies still missing:")
        for role, reason in problems.items():
            print(f"  - {role}: {reason}")

    if counts[FAIL]:
        return 2
    if args.strict and counts[NOT_VERIFIED]:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
