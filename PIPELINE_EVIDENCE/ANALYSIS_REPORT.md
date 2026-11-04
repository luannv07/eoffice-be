# Phân tích bằng chứng CI/CD và Security Scan

## A. Tổng quan dữ liệu

Phạm vi đã đọc là toàn bộ cây `PIPELINE_EVIDENCE/`, gồm metadata, execution log,
artifact đã tải và báo cáo JSON/XML/HTML/CSV. Không thay đổi cấu hình hay dữ liệu gốc.

| Pipeline | Workflow run | Success | Failure | Cancelled | Skipped |
|---|---:|---:|---:|---:|---:|
| PR Gate | 280 | 99 | 164 | 17 | 0 |
| Dev Secure Deploy | 101 | 70 | 29 | 2 | 0 |
| Scheduled Deep Security Scan | 154 | 35 | 116 | 3 | 0 |
| DAST | 48 | 24 | 12 | 1 | 11 |
| **Tổng** | **583** | **228** | **321** | **23** | **11** |

Đã kiểm kê 31.774 file: 583 `execution-log.txt`, 583 `metadata.json`, 888 file
JSON/XML/HTML/CSV và 30.018 file nằm trong các thư mục `artifacts`. Những số này
là số file bằng chứng, không phải số artifact logic của GitHub.

Thiếu/không đọc được đáng kể: run DAST `39013572248` báo lỗi tạo report
`AccessDeniedException /zap/wrk/zap_report.html`; log cũng ghi không tìm thấy JSON
report và không tải được artifact (`execution-log.txt`, dòng 256--277). Không có
`TEST-*.xml`, Surefire hay Failsafe report trong cây dữ liệu.

Trạng thái dùng trong báo cáo: **VERIFIED** = có bằng chứng trực tiếp;
**PARTIALLY VERIFIED** = có bằng chứng nhưng chưa đủ kết luận; **NOT VERIFIED** =
không có bằng chứng phù hợp; **FAILED** = bằng chứng cho thấy lần kiểm thử thất bại.

## B. Bảng kết quả kiểm chứng

| ID | Yêu cầu | Trạng thái | Bằng chứng | Nhận xét |
|---|---|---|---|---|
| P0.1 | HTTP status và 101 URL | PARTIALLY VERIFIED | `DAST_SCAN/41033389256/execution-log.txt`: 301, 308, 324, 370; `.../scanned_urls.txt`; `zap_report.json`: 6--39 | Có 95 URL OpenAPI, spider 103, export 106 message/node, 106 dòng nhưng chỉ 81 URL unique. Report ZAP có tỷ lệ 4xx 97%, 2xx 1%, 5xx 1%, không có count/mapping theo request; 101 URL không được chứng minh. |
| P0.2 | Đối chiếu 4 Low và 7 WARN | VERIFIED | `DAST_SCAN/39013572248/execution-log.txt`: 214--260; `DAST_SCAN/41033389256/execution-log.txt`: 301--373, 412--414 | Là hai lần khác nhau, khác commit và cấu hình. Không chứng minh rủi ro giảm. |
| P0.3-1 | BOLA/IDOR | NOT VERIFIED | Không có request/response, status và assertion kiểm thử BOLA/IDOR | Nội dung commit/source không là bằng chứng kiểm thử nghiệp vụ. |
| P0.3-2 | Workflow state bypass | NOT VERIFIED | Không có test case hay request/response xác thực | Không thể suy ra từ workflow CI hoặc ZAP. |
| P0.3-3 | Refresh token reuse | NOT VERIFIED | Không có request/response/token-rotation assertion trong evidence | Cần test hai lần dùng cùng refresh token đã rotate và log/response đã che bí mật. |
| P0.3-4 | Invalid upload | NOT VERIFIED | Không có test case, request/response hay log chặn upload | Cần các ca sai định dạng, quá dung lượng và MIME giả. |
| P1.1 | DAST gắn với commit staging | PARTIALLY VERIFIED | `DAST_SCAN/41033389256/metadata.json`: trường `head_sha`, `head_branch`, `event`, `conclusion`; `execution-log.txt`: 129, 153, 370 | DAST thành công trên commit `67bc905…`, branch `dev`, scan có JWT. Không có chứng cứ độc lập rằng staging đang chạy đúng image/commit này tại lúc scan. |
| P1.2 | PR Gate commit `67bc905` | NOT VERIFIED | `ALL_PIPELINE_RUNS.csv`, dòng 282 có commit ở Dev Secure Deploy; truy vấn PR Gate không có commit này | Cần chạy/lưu PR Gate cho chính commit hoặc SHA merge phù hợp. |
| P1.3 | Surefire/Failsafe reports | NOT VERIFIED | Không có `TEST-*.xml`, thư mục/file Surefire hoặc Failsafe | Không thể tổng hợp total/pass/fail/skip hoặc suite lỗi. |
| P1.4 | Flaky tests | NOT VERIFIED | Có nhiều run success/failure nhưng không có report theo test case | Chưa đủ dữ liệu để quy lỗi cho test flaky, code hay môi trường. |
| P2 | RBAC và multi-role | NOT VERIFIED | Không có bài kiểm thử API có principal/role, request, response và assertion | Cấu hình/artefact source không thay thế bằng chứng thực thi. |

## C. Phân tích theo pipeline

### Pipeline 1 -- PR Gate

- Có 280 run (99 success, 164 failure, 17 cancelled), chi tiết tại
  `ALL_PIPELINE_RUNS.csv` và `PR_GATE/all-runs.json`.
- Không có run nào của PR Gate với `head_sha` bắt đầu `67bc905`.
- Một run có artifact là `PR_GATE/34948428347`: log cho thấy job `Security Scan
  (Trivy + TruffleHog)` và artifact `sonar-artifacts`; đây là bằng chứng pipeline
  đã thực hiện các công cụ này ở run đó, không phải kết quả kiểm thử nghiệp vụ.
- Artifact `sonar-artifacts` chứa class/source và JaCoCo; không có Surefire/Failsafe
  result để xác nhận kết quả theo từng test.

### Pipeline 2 -- Dev Secure Deploy

- Có 101 run (70 success, 29 failure, 2 cancelled). Run `40287830428` là push
  branch `dev`, commit `67bc9052d519ecb7e05cd23c56c719836e50511e`, kết thúc success:
  `DEV_SECURE_DEPLOY/40287830428/metadata.json`, dòng 5--22.
- Job `Build, Scan, Push and Deploy to Heroku` có Maven `BUILD SUCCESS` (dòng 205
  và 2189), Docker build/push tag SHA (dòng 210--2222) và sau đó thực hiện health
  check (dòng 2305--2314). Đây là bằng chứng build/deploy workflow thành công, nhưng
  endpoint và bằng chứng version đang phục vụ bị che/không có ở artifact.

### Pipeline 3 -- Scheduled Deep Security Scan

- Có 154 run (35 success, 116 failure, 3 cancelled); không được bỏ qua tỷ lệ failure
  cao này khi lập luận độ ổn định.
- Run thành công `40778668779` có job `Deep Security Scan`: cài Trivy, xuất
  `security-reports/trivy-full.json`, upload `deep-security-reports-40778668779`,
  rồi build Maven thành công. Xem `SCHEDULED_DEEP_SCAN/40778668779/execution-log.txt`,
  dòng 278--353 và 3188--3200. Nó chứng minh scan được chạy, không tự nó chứng minh
  mọi phát hiện đã được khắc phục.
- Với commit `67bc905…`, các run schedule 40411783419, 40590399380, 40778668779 là
  success; 40966273075 là failure (`ALL_PIPELINE_RUNS.csv`, dòng 383--386).

### Pipeline 4 -- DAST

- Có 48 run (24 success, 12 failure, 1 cancelled, 11 skipped) và nhiều report ZAP.
- Run `41033389256` (2026-10-30) thành công, branch `dev`, commit `67bc905…`, event
  `schedule` (`metadata.json`, dòng 5--22). Log xác nhận authenticated JWT scan
  (dòng 139--153), context `EPU-EOffice-API`, OpenAPI import, `scanOnlyInScope=true`,
  spider user `dast-user`, depth 5/duration 10, active scan context và exit threshold
  High (dòng 270--301). Đây là giới hạn phạm vi thực tế cần nêu trong báo cáo.
- Lần này OpenAPI thêm 95 URL, spider phát hiện 103 URL, export 106 message/node;
  `scanned_urls.txt` có 106 dòng. Số này không đồng nghĩa 106 URL duy nhất: file có
  lặp URL. Báo cáo không có HTTP status code cho từng URL; chỉ ghi spider gặp một URL
  trả 500 trong log (dòng 307, 329). Vì thế không thể xác định số URL phản hồi và
  phân bố status 2xx/4xx/5xx từ evidence hiện có.
- Run mới ghi High 0, Medium 0, Low 4 (`execution-log.txt`, dòng 370--373,
  412--414). Báo cáo ZAP gốc nằm tại
  `DAST_SCAN/41033389256/artifacts/dast-zap-reports-41033389256/zap_workspace/reports/zap_report.json`;
  các Low có `riskcode: "1"` ở dòng 108, 468, 498, 528.

### Đối chiếu 7 WARN và 4 Low

`7 WARN` thuộc run `39013572248`, ngày 2026-10-17, commit `f79ebcd…`, event
schedule, workflow failure (`metadata.json`, dòng 5--22). Đây là baseline scan cũ:
log ghi bảy `WARN-NEW` rule distinct (HSTS, CSP, Non-Storable Content, Permissions
Policy, Timestamp Disclosure, Modern Web Application, Sec-Fetch-Dest; dòng 214--255),
đồng thời spider bị 401/404 (dòng 258--260), ZAP exit code 2 và report JSON bị thiếu.

`4 Low` thuộc run `41033389256`, ngày 2026-10-30, commit `67bc905…`, workflow success,
authenticated JWT scan, có OpenAPI + spider + active scan và export report. `WARN-NEW`
là phân loại/threshold của baseline automation plan cũ; `Low` là risk level của report
mới. Chúng khác thời điểm, commit, kiểu scan, authentication, URL coverage và rule
threshold. Không có mapping alert-to-alert giữa hai run; không được ghi rằng “7 WARN
đã giảm thành 4 Low” hay rủi ro đã giảm.

## D. Nội dung đủ bằng chứng để đưa vào báo cáo

1. Có bốn workflow và 583 run được cung cấp; kết quả gồm cả run failure/cancelled như
   Bảng A.
2. Dev Secure Deploy `40287830428` đã hoàn tất success cho commit `67bc905…` trên
   branch dev, có Maven build success, Docker build/push và health-check step.
3. DAST `41033389256` đã chạy authenticated scan trên commit `67bc905…`; evidence
   trực tiếp ghi OpenAPI 95, spider 103, export 106 message/node, High 0, Medium 0,
   Low 4. Câu này chỉ mô tả kết quả run và không suy rộng thành bảo mật toàn diện.
4. DAST cũ `39013572248` có bảy `WARN-NEW` distinct và workflow failure; report của
   run đó không được tạo/tải thành công.

## E. Nội dung chưa đủ bằng chứng

- **HTTP status/101 URL:** thu `ZAP history` hoặc HAR/JSON gồm URL, method, response
  status, timestamp; cần phân biệt imported, discovered, requested và unique URL.
- **Tuyên bố cải thiện từ 7 WARN sang 4 Low:** chạy hai cấu hình giống nhau trên cùng
  target, cùng commit và cùng rule set; lưu alert inventory và diff từng rule/instance.
- **BOLA, bypass state, refresh reuse, invalid upload, RBAC:** bổ sung API/integration
  tests có dữ liệu hai user/two org, role đơn và đa role; lưu request đã redaction,
  response status/body assertion và log correlation ID. Test phải có expected denial
  (403/404 theo thiết kế), expected state transition và PASS/FAIL rõ ràng.
- **PR Gate `67bc905`:** chạy PR Gate cho SHA này hoặc SHA merge tương đương, lưu
  metadata, execution log và artifact.
- **Test totals và flaky:** upload Surefire/Failsafe XML cho mỗi run; theo dõi test
  name, attempts, failure stack trace và nguyên nhân môi trường/dependency/timeout.
- **Staging version:** lưu deploy receipt/release ID, image digest/SHA và response
  version endpoint tại thời điểm DAST.

## F. Kế hoạch hoàn thiện theo ưu tiên

| Ưu tiên | Hành động | Đầu ra cần lưu |
|---|---|---|
| P0 | Chạy DAST cùng cấu hình và export request history/status từng URL | HAR/ZAP history + URL/status summary + report JSON/XML |
| P0 | Chạy test nghiệp vụ BOLA, state bypass, refresh reuse, invalid upload | Test report, request/response redacted, assertion, correlation log |
| P0 | Lặp lại baseline và authenticated DAST trên cùng SHA/target/rule set | Alert diff theo rule/instance và cấu hình scan versioned |
| P1 | Chạy PR Gate cho `67bc905…` | Metadata, full job log, scan and test artifacts |
| P1 | Upload Surefire/Failsafe | `TEST-*.xml` và test summary machine-readable |
| P1 | Gắn staging với SHA scan | Release/image digest và endpoint version evidence |
| P2 | Ma trận RBAC cho USER, REVIEWER, LEADER, CLERK, ADMIN, SYSTEM_ADMIN và multi-role | API test matrix, expected/actual status, audit logs |

## G. Kết luận cuối cùng

1. **Chưa đủ** để hoàn thiện phần CI/CD và Security Testing theo toàn bộ yêu cầu.
   Có bằng chứng đáng tin cậy về việc workflow/scan đã chạy, nhưng còn thiếu bằng
   chứng status HTTP đầy đủ, kiểm thử nghiệp vụ/RBAC, test result chuẩn và mapping
   staging-version.
2. Có thể khẳng định các run và kết quả cụ thể nêu ở mục D, gồm DAST 106 message/node,
   High/Medium 0 và Low 4 cho đúng run `41033389256`.
3. Tuyệt đối chưa nên ghi rằng BOLA/IDOR, workflow bypass, refresh-token reuse,
   invalid upload hoặc RBAC đã được kiểm thử thành công; cũng không nên ghi 7 WARN đã
   giảm thành 4 Low, hoặc staging đã quét đúng phiên bản chỉ dựa vào workflow SHA.
4. Thực hiện các hạng mục P0 trước, sau đó bổ sung PR Gate và test XML/staging linkage
   ở P1 để chốt báo cáo có thể truy vết.
