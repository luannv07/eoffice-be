# Báo cáo hiện trạng CI/CD Pipeline — `eoffice-be`

> **Phạm vi:** Pipeline 1/2/3 trên GitHub Actions của repo `luannv07/eoffice-be`
> **Nguồn dữ liệu:** `gh` CLI (API actions/runs + jobs + logs thật) — không ước lượng
> **Thời điểm truy vấn:** 2026-09-13
> **Cửa sổ phân tích:** 300 run gần nhất, dải ngày **2026-05-29 → 2026-09-13** (tổng thực tế 313 run)

---

## 1. Cách pipeline hoạt động (định vị)

Repo có **3 pipeline**, tách theo mục đích và trigger:

| Pipeline | File | Trigger | Vai trò |
|---|---|---|---|
| **1. PR Gate** | `pipeline-1-pr-gate.yml` | `pull_request` → `dev` (opened/synchronize/reopened) | Chặn/rà soát code + security **trước khi merge** |
| **2. Dev Secure Deploy** | `pipeline-2-dev-secure-deploy.yml` | `push` → `dev` (sau merge) | Build image, scan, **deploy staging/VPS** |
| **3. Scheduled Deep Security Scan** | `pipeline-3-scheduled-deep-security-scan.yml` | `schedule` | Quét sâu dependency/CVE định kỳ |

### Các bước của Pipeline 1 (PR Gate) — thứ tự tuyến tính, fail 1 bước ⇒ job fail
1. Checkout (`fetch-depth: 0` để tính diff base↔head)
2. Setup Java 21 (temurin)
3. **Build + test + static analysis** → `mvn -B clean verify`
4. Secret scan (TruffleHog) — `continue-on-error: true` ⇒ **không bao giờ chặn**
5. SonarQube Scan + Quality Gate → `mvn sonar:sonar` (có `SONAR_TOKEN`)
6. Setup Node.js 24
7. Checkout repo phụ `eoffice-ai-review` (bằng `GH_PAT`)
8. `npm install` cho AI reviewer
9. **AI Code Review** → chạy `ai_service.ts` (đa provider: Hy3/Groq/OpenRouter/Gemini/17WTF)
10. Finish (luôn chạy)

**Cơ chế gate của bước 9** (xem `eoffice-ai-review/ai_service.ts`):
- `FAIL_ON_SEVERITIES = ["blocking"]` (`ai_service.ts:142`)
- AI gắn nhãn mỗi issue: `blocking` / `important` / `nit` / `suggestion`
- **Chỉ khi có ≥1 issue `blocking` ⇒ `process.exit(1)`** ⇒ pipeline đỏ (`ai_service.ts:1502`, `:1533`)
- `important` / `nit` / `suggestion` ⇒ `exit(0)` ⇒ **pipeline vẫn xanh**

> ⇒ **Đây chính là lý do PR #27 "toàn important mà vẫn passed"**: gate được thiết kế *chỉ chặn mức `blocking`*. `important` chỉ là cảnh báo + comment inline, không block.

---

## 2. Chỉ báo ổn định tổng quan

### 2.1 Theo pipeline (300 run)

| Pipeline | Tổng | ✅ Success | ❌ Failure | ⚠️ Cancelled | Tỷ lệ đỏ |
|---|---:|---:|---:|---:|---:|
| **1. PR Gate** | 145 | 68 | 69 | 8 | **~48%** |
| **2. Dev Secure Deploy** | 48 | 30 | 17 | 1 | ~35% |
| **3. Deep Security Scan** | 107 | 22 | 82 | 3 | **~77%** |

> 8 cancelled ở PR Gate đến từ `concurrency: cancel-in-progress: true` (push liên tục ⇒ run cũ bị hủy) — **đây là hành vi chủ đích, không phải lỗi.**

### 2.2 PR Gate — 69 run đỏ **thực chất là gì?** (phân tích step-level + log)

| Bước thất bại | Số run | Bản chất |
|---|---:|---|
| **3. Build (Tests + Static)** | 34 | Lỗi code/thật — test fail (chủ yếu), ít compile error |
| **9. AI Code Review** | 33 | **24 = gate chặn ĐÚNG** (tìm thấy `blocking`) · **9 = lỗi hạ tầng/API** |
| **5. SonarQube Quality Gate** | 2 | Chất lượng code dưới ngưỡng |

**Kết luận ổn định cho PR Gate:** phần lớn "đỏ" là **pipeline đang làm đúng việc** (bắt test lỗi + chặn `blocking`), **không phải mất ổn định**. Mất ổn định hạ tầng thật sự (AI provider lỗi) **chỉ tập trung 9 run đầu tiên** (2026-05-29 → 2026-06-01, toàn commit "test push"/"code base" giai đoạn khởi dựng) — **các tháng sau gần như không còn**.

### 2.3 Xu hướng theo tháng (PR Gate)

| Tháng | Run | Success | Failure | Cancelled | Ghi chú |
|---|---:|---:|---:|---:|---|
| 2026-05 | 17 | 7 | 10 | 0 | Tuần đầu dựng pipeline, nhiều flaky/API lỗi |
| 2026-06 | 2 | 1 | 1 | 0 | |
| 2026-08 | 12 | 5 | 5 | 2 | |
| 2026-09 | 114 | 55 | 53 | 6 | Sprint chính — cường độ rất cao |

---

## 3. Khi push/merge thì pipeline check những gì

### 3.1 PR Gate kiểm tra (mỗi lần mở/sync PR)
1. **Compile + unit/integration test** (`mvn verify`) — đỏ nếu test/error/compilation fail
2. **Secret scan** (TruffleHog) — cảnh báo, **không chặn** (`continue-on-error`)
3. **SonarQube Quality Gate** — chặn nếu vi phạm ngưỡng
4. **AI review đa lớp** — chặn **chỉ khi `blocking`** (security, crash/cháy dữ liệu chắc chắn, auth bypass, break hợp đồng API nghiêm trọng)

### 3.2 Pipeline 2 — deploy sau merge (`push`→`dev`, 48 run, 17 đỏ)

| Bước đỏ | Số run | Nguyên nhân phổ biến |
|---|---:|---|
| **Trivy scan image** | 6 | CVE trong Docker image/base image |
| **Health check** | 7 | App sau deploy không trả health OK (DB/migration/config) |
| **Push Docker image** | 2 | Lỗi registry/network transient |
| **Deploy via SSH** | 2 | VPS/SSH transient |

### 3.3 Pipeline 3 — scheduled deep scan (107 run, **82 đỏ — gần như luôn đỏ**)

- **81/82** đỏ tại bước **"3. OWASP Dependency Check"** do phát hiện CVE CVSS cao, ví dụ:
  - `spring-core-6.2.3.jar` → `CVE-2026-47884`…(nhiều mã CVSS **9.8**)
  - `spring-security-core-6.5.11.jar` → `CVE-2026-59270` (9.1)
- Dependency-Check `exit 1` khi có CVE ≥ threshold ⇒ **fail by design**, nhưng vì **CVE không được remediate** nên pipeline đỏ liên tục.

---

## 4. Các lỗi PHỔ BIẾN khi chạy pipeline (dẫn chứng thật từ log)

### Nhóm A — Test fail (chiếm đa số đỏ ở bước 3)
| Test / signature | Lỗi | Nguyên nhân gợi ý |
|---|---|---|
| `PdfProcessingServiceTest.signAndCertifyPdf_shouldSignSuccessfully:112` | `CMS IOException reading content` | Tài nguyên/cert để ký PDF thiếu hoặc sai đường dẫn trong môi trường CI |
| `AuthCoreContextTest.contextLoadsAndJwtRoundTripWorks` | `IllegalState Failed to load ApplicationContext` | Spring context không boot được (DB/Liquibase/config) |
| `DocumentWorkflowControllerTest.testGetPendingReviewDocuments_*` | `ApplicationContext failure threshold (1) exceeded` | Lỗi context dây chuyền ⇒ cả suite cascade fail (Tests 387, 8 errors) |
| `RefreshTokenServiceTest.rotateShouldRevokeOldAndIssueNewToken:49` | `Business: Phiên đăng nhập đã hết hạn` | **Test phụ thuộc thời gian (time-based)** → flaky |
| `DocumentServiceCoverageTest.*` | `Mockito` (stubbing/unnecessary) | Mock không khớp signature mới |
| 2 run | `COMPILATION ERROR` | Code không biên dịch được |

### Nhóm B — AI gate chặn (`blocking`, 24 run) — *đúng thiết kế*
- Security/auth-bypass, hợp đồng API, khả năng crash/cháy dữ liệu mà AI xếp `blocking`.

### Nhóm C — Hạ tầng/API AI (9 run, dồn tuần đầu)
- `Hy3 API Error: 429` (vượt RPM/TPM limit), `504 openai_error`, `fetch failed`
- `All AI providers failed for <file>` ⇒ **soft-fail**, nhưng giai đoạn đầu có lần chết fatal ⇒ đỏ.

### Nhóm D — Sonar (2 run), Trivy/health/CVE (Pipeline 2&3) như mục 3.2/3.3.

---

## 5. Rủi ro kiến trúc phát hiện được (quan trọng)

1. **Branch `dev` KHÔNG có branch protection** (API trả `404 Branch not protected`).
   ⇒ **Mọi "gate" hiện chỉ mang tính cảnh báo** — PR vẫn merge được dù PR Gate **đỏ**. Thực tế 18/27 PR đã merge bất chấp có run fail.
   ⇒ Gate **không thực sự chặn** ở tầng GitHub. Cần bật *require status checks* nếu muốn enforce.

2. **`important` không block** (thiết kế `FAIL_ON_SEVERITIES=["blocking"]`). Nghĩa là issue "nên sửa trước khi merge" có thể bị bỏ qua vô hạn — đúng như thắc mắc PR #27.

3. **Secret scan không chặn** (`continue-on-error: true`) ⇒ có secret lọt vẫn xanh bước đó.

4. **Pipeline 3 đỏ thường trực** (77%) do CVE tồn đọng ⇒ tín hiệu "alarm fatigue", khó phân biệt regress mới vs CVE cũ.

5. **Test dễ gãy theo môi trường/thời gian** (CMS/PKI resource, ApplicationContext, refresh-token time-based) ⇒ đỏ không phải lúc nào cũng do code mới sai.

---

## 6. Khuyến nghị (ưu tiên theo tác động)

**Bảo vệ merge (cao):**
- Bật **branch protection `dev`**: required status check = `PR Gate`, `Require branches to be up to date`, restrict admin bypass. Không có bước này thì mọi kết luận ở trên chỉ là "đèn trang trí".

**Cải thiện tín hiệu gate PR (cao):**
- Cân nhắc `FAIL_ON_SEVERITIES = ["blocking","important"]` **hoặc** thêm cơ chế "blocking OR ≥ N important" để `important` không vô hạn.
- Bỏ `continue-on-error` ở secret scan (bước 4) ⇒ chặn secret thật sự.

**Giảm đỏ giả (trung bình):**
- Sửa test time-based (`RefreshTokenServiceTest`) → đồng hoá thời gian (Clock injected) để khỏi flaky.
- Làm cho `ApplicationContext` test độc lập DB/Liquibase (schema/seed determinstic) để tránh cascade `failure threshold exceeded`.
- Cung cấp cert/resource ký PDF trong CI để sửa `CMS IOException`.

**Dependency/CVE (trung bình):**
- Nâng `spring-core`/`spring-security` lên bản vá; hoặc đặt `failBuildOnCVSS` + **baseline/suppression file** để Pipeline 3 chỉ đỏ khi CVE **mới**, giảm alarm fatigue.

**AI provider (thấp, đã ổn):**
- Thêm **exponential backoff + jitter** khi 429/504 (chỉ còn ảnh hưởng tuần đầu). Giữ `SOFT_FAIL` để API lỗi không làm đỏ deploy.

---

## 7. Tổng kết một câu

**Pipeline nhìn chung HOẠT ĐỘNG ĐÚNG THIẾT KẾ và ngày càng ổn định** (lỗi hạ tầng AI dồn hết ở tuần khởi dựng tháng 5, không tái diễn); tỉ lệ "đỏ" cao chủ yếu vì nó **bắt đúng lỗi thật** (test + blocking). **Điểm yếu lớn nhất không phải độ ổn định mà là tính enforce**: branch `dev` chưa có protection ⇒ gate chỉ mang tính cảnh báo, cộng với việc `important` không chặn merge chính là lý do PR #27 xanh dù còn nhiều issue nên sửa.

---

*Phụ lục — độ chắc chắn:* Số liệu run/step ở mục 2–3 trích trực tiếp từ GitHub Actions API (`group_by` conclusion + step-level failure + log). Phân loại "gate block vs infra" cho bước 9 dựa trên sự hiện diện của dòng `[GATE] Blocking issues detected` trong log (authoritative). Vài run rất cũ (5/2026) log có thể đã hết hạn 90 ngày ⇒ ký hiệu `(log-expired)`.
