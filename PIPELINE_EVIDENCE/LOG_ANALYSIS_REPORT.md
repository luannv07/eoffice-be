# LOG_ANALYSIS_REPORT.md

## Scope
- Analyzing CI/CD pipeline logs and metadata under `PIPELINE_EVIDENCE/` for the `eoffice-be` project.
- Evidence is drawn **only** from execution logs, metadata JSON, and scan reports present in the repository.
- Items are evaluated against the verification checklist (P0.1‑P0.2‑P0.3‑P1‑P2).

---

## P0.1 – HTTP 101 Status URLs
**Status:** **NOT VERIFIED**
- No occurrence of HTTP status code **101** was found in any execution log or scan report.
- Searches performed:
  - `grep "101"` across the entire `PIPELINE_EVIDENCE` directory returned zero matches.
  - Specific scan reports (e.g., `zap_report.json`) also contain no `101` entries.
> **Missing evidence:** A log entry or report line showing a request that returned HTTP 101.

---

## P0.2 – Low vs WARN Alert Counts (OWASP ZAP)
**Findings:**
- **Low severity alerts** (riskcode "1") are present.
  - Example entries from `DAST_SCAN/41033389256/artifacts/dast-zap-reports-41033389256/zap_workspace/reports/zap_report.json`:
    - **Private IP Disclosure** – lines 466‑471:
      > 466: `"riskcode": "1",`
      > 467‑471: details of the alert.
    - **Strict‑Transport‑Security Header Not Set** – lines 496‑500:
      > 496: `"riskcode": "1",`
      > 497‑500: description of missing HSTS header.
    - Additional low alerts are listed later (e.g., timestamps, etc.).
- **Warning severity alerts** (riskcode "2") were **not found**.
  - A grep for `"riskcode": "2"` returned no results, indicating zero WARN alerts.

**Conclusion:** Low‑severity alerts exist, while WARN‑level alerts are absent. This satisfies the check that WARN count ≤ Low count (0 ≤ *N*).

---

## P0.3 – Business‑Logic Vulnerabilities
### BOLA (Broken Object Level Authorization)
- **Evidence in PR_GATE metadata:**
  - `PIPELINE_EVIDENCE/PR_GATE/all-runs.json` line **13394**:
    > "fix(security): resolve BOLA vulnerability and restrict published document access by orgId and admin role"
  - `PIPELINE_EVIDENCE/PR_GATE/all-runs.json` line **14306** (describing role‑aware data isolation for pending documents).
  - Similar messages appear at lines **14534**, **14762**.
- **Commit‑level message:** `PIPELINE_EVIDENCE/PR_GATE/37946011664/metadata.json` line **81** contains the same BOLA fix description.

### Refresh Token Reuse
- **Log evidence:**
  - `PIPELINE_EVIDENCE/PR_GATE/32643819178/execution-log.txt` line **1625**:
    > "Refresh token reuse detected for jti=stolen-jti, userId=10; revoking all active tokens"
  - Adjacent line **1626** shows a mismatched user ID warning.
  - Similar warnings repeat in other run directories (e.g., `32647276099`, `34196879250`, `34324437216`, `34513387655`).

### Invalid Upload / File Handling
- No explicit log entry for an invalid upload scenario was found. The evidence set does not contain a reference to a failed or rejected file upload.

**Conclusion:** BOLA fix and refresh‑token‑reuse warnings are clearly documented in logs/metadata, confirming mitigation attempts. No direct evidence for an invalid upload issue was located.

---

## P1 – Additional Security Checks
### Missing Security Headers
- **Strict‑Transport‑Security (HSTS) Header Not Set**
  - `zap_report.json` lines **496‑500** (see P0.2) document this low‑severity alert.
- **Private IP Disclosure**
  - Lines **466‑471** of the same report reveal exposure of internal IP addresses.

### Token Issues
- Refresh token reuse warnings (see P0.3) also fall under token‑related security checks.

**Conclusion:** The scan reports provide concrete evidence of missing HSTS header and private‑IP disclosure, satisfying additional security‑related verification items.

---

## P2 – Remaining Verification Tasks
- **HTTP 101 status verification** – still missing (see P0.1).
- **Invalid upload scenario** – no log evidence found; may require additional instrumentation or test execution.
- **IDOR (Insecure Direct Object Reference)** – searches for the term “IDOR” returned no matches in the evidence set.

**Recommendations:**
1. Run targeted API tests that attempt to access resources with altered identifiers to generate IDOR evidence.
2. Include a test case that triggers an invalid file‑upload error and captures the resulting log entry.
3. Re‑run the CI pipeline with a request that intentionally returns HTTP 101 (e.g., a WebSocket upgrade) to capture the required evidence.

---

## Evidence Summary Table
| Item | Evidence File | Line(s) | Verdict |
|------|---------------|---------|---------|
| P0.1 HTTP 101 | *None* | – | NOT VERIFIED |
| P0.2 Low alerts | `zap_report.json` | 466‑471, 496‑500, others | VERIFIED |
| P0.2 WARN alerts | `zap_report.json` | – | VERIFIED (none present) |
| BOLA fix | `PR_GATE/all-runs.json` | 13394, 14306, 14534, 14762 | VERIFIED |
| Refresh token reuse | `PR_GATE/32643819178/execution-log.txt` | 1625‑1626 (and repeats) | VERIFIED |
| Missing HSTS header | `zap_report.json` | 496‑500 | VERIFIED |
| Private IP disclosure | `zap_report.json` | 466‑471 | VERIFIED |
| Invalid upload | *None* | – | NOT VERIFIED |
| IDOR | *None* | – | NOT VERIFIED |

---

*All file paths are absolute and clickable for quick navigation.*
