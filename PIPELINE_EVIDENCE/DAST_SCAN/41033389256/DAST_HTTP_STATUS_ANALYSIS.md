# DAST HTTP status và URL coverage -- run 41033389256

## Kết luận

**NOT VERIFIED** đối với con số “101 URL duy nhất”, số request HTTP, số response, và thống kê status code theo URL. Evidence gốc chứng minh DAST đã hoàn tất và có một export History gồm 106 message/node, nhưng không chứa HTTP History/HAR hoặc session database để liên kết từng message với method, status code và response.

## A. Phạm vi dữ liệu đã kiểm tra

Run: `41033389256`; workflow/job: `Pipeline 4 – Dynamic Application Security Testing (DAST)` / `OWASP ZAP Dynamic Security Scan`; commit `67bc9052d519ecb7e05cd23c56c719836e50511e`; ngày 2026-10-30.

Đã kiểm tra toàn bộ 7 file trong `DAST_SCAN/41033389256/`:

| File | Kết quả kiểm tra |
|---|---|
| `metadata.json` | Xác nhận commit, branch `dev`, event `schedule`, conclusion `success`. |
| `execution-log.txt` | Có cấu hình/tiến trình OpenAPI, Spider, Active Scan và export History. |
| `artifact-download.txt` | Chỉ ghi quá trình tải artifact. |
| `.../reports/scanned_urls.txt` | 106 dòng URL export từ ZAP History. |
| `.../reports/zap_report.json` | Alert/report và ZAP insights dạng phần trăm; không có HTTP message detail. |
| `.../reports/zap_report.xml` | Bản XML cùng loại nội dung report; không có history detail. |
| `.../reports/zap_report.html` | Bản HTML cùng report; không có HAR/history table. |

Không có file session (`.session`, `.data`, `.db`, SQLite), HAR, request/response JSON, hoặc export ZAP HTTP History chi tiết trong artifact đã tải. Vì vậy không có cơ sở để điền method, status code, response availability cho từng URL.

## B. Đối chiếu các tập URL

| Loại dữ liệu | Số lượng | Trạng thái | Nguồn và phương pháp |
|---|---:|---|---|
| URL OpenAPI import | 95 | VERIFIED | `execution-log.txt:300--302`, `Job openapi added 95 URLs`. |
| URL Spider phát hiện | 103 | VERIFIED | `execution-log.txt:305--309`, `Job spider found 103 URLs`. Log không xuất danh sách Spider. |
| ZAP History message/node export | 106 | VERIFIED | `execution-log.txt:297--298,324`; export source = `History`, rồi `Exported 106 message(s) / node(s)`. |
| Dòng trong `scanned_urls.txt` | 106 | VERIFIED | Đếm các dòng `http(s)` trong file; khớp với gate ở `execution-log.txt:345--370`. |
| URL duy nhất trong `scanned_urls.txt` | 81 | VERIFIED | Trim whitespace, so sánh chuỗi URL tuyệt đối, sort/unique. Chuẩn hóa bỏ trailing slash vẫn là 81. |
| Dòng trùng trong export | 25 | VERIFIED | `106 - 81`; đây là bản ghi History lặp URL, không phải 25 endpoint độc lập. |
| URL duy nhất “101” | Không chứng minh được | NOT VERIFIED | Không có tập URL nguồn nào cho ra 101 sau phương pháp đếm có thể tái lập. |
| URL thực sự nhận HTTP request | Không xác định | NOT VERIFIED | History export chỉ có URL, không có request/message record. |
| URL có HTTP response | Không xác định | NOT VERIFIED | Không có status/message/HAR theo URL. |
| URL được Active Scan | Không xác định theo URL | PARTIALLY VERIFIED | Active Scan chạy và hoàn tất (`execution-log.txt:312--313`), nhưng không có target/message list. |
| URL chỉ do Spider phát hiện | Không xác định | NOT VERIFIED | Log cho tổng 103 nhưng không có danh sách để set-difference với OpenAPI/History. |

Khác biệt 95/103/106/81 là do các phép đo khác nhau: import OpenAPI, kết quả Spider, bản ghi History và URL unique. Không có evidence để phân rã chính xác phần giao giữa OpenAPI và Spider, hay để coi 106 record là 106 URL unique/request.

Các bản ghi trùng rõ nhất: `/auth/login` xuất hiện 12 lần; bảy URL resource-ID xuất hiện 3 lần mỗi URL (như `documents/10`, `admin/users/10`, `dictionary-items/10`). Danh sách nguồn `scanned_urls.txt` được giữ nguyên; không chỉnh sửa evidence gốc.

## C. HTTP status statistics

| HTTP status/class | Số request | Số URL duy nhất | Evidence trực tiếp | Ý nghĩa |
|---|---:|---:|---|---|
| 2xx | Không xuất | Không xuất | `zap_report.json:24--31`, insight `insight.code.2xx`, statistic `1` | ZAP báo **1% response 2xx**; không phải count. |
| 4xx | Không xuất | Không xuất | `zap_report.json:6--14`, insight `insight.code.4xx`, statistic `97` | ZAP báo **97% response 4xx**; không thể phân tách 400/401/403/404. |
| 5xx | Không xuất | Không xuất | `zap_report.json:32--39`, insight `insight.code.5xx`, statistic `1` | ZAP báo **1% response 5xx**. |
| 500 | Ít nhất một Spider access error | Không xác định | `execution-log.txt:306--307,329` | URL bị che (`***`); log nói status 500 khi Spider kỳ vọng 200. Không suy ra số request unique. |
| 200, 201, 301, 302, 400, 401, 403, 404 | Không xuất | Không xuất | Không có message/status table | Không gán giá trị từ alert hoặc URL list. |
| Other / No response | Không xác định | Không xác định | Không có HTTP History/HAR | Không thể tính. |

Các phần trăm 97% + 1% + 1% không cung cấp mẫu số và không có bảng mapping message-to-status. Không được quy đổi chúng thành count trên 106 export records, vì 106 là message/node export chứ không được chứng minh là cùng mẫu số của insight.

## D. Danh sách URL và request/response

`scanned_urls.txt` cung cấp URL-only. Không có HTTP method, status code hoặc response payload/header. Do đó không thể tạo danh sách hợp lệ theo schema `URL | method | status | response available | source` mà không bịa dữ liệu. File chi tiết CSV không được tạo vì nó sẽ chỉ gồm các ô “unknown”, dễ bị hiểu nhầm là bằng chứng request/response.

81 URL unique và số lần xuất hiện có thể tái tạo bằng PowerShell read-only:

```powershell
Get-Content scanned_urls.txt | Where-Object { $_ -match '^https?://' } |
  Group-Object | Sort-Object Name | Select-Object Name, Count
```

## E--F. URL chưa chứng minh request/response và lỗi HTTP

- Không thể xác định URL nào được phát hiện nhưng không gửi request, vì Spider results và message history detail không được export.
- Có ít nhất một Spider request trả 500, nhưng URL đã bị mask trong log; `zap_report.json` chỉ cho tỷ lệ class 5xx 1%.
- ZAP insight báo response 4xx chiếm 97%; không xác định được URL, method, hay cụ thể 401/403/404. Không dùng các alert evidence để gán status.
- Không có evidence về số URL “no response”.

## G. Phạm vi thực tế Spider và Active Scan

| Cấu hình/sự kiện | Evidence | Tác động coverage |
|---|---|---|
| JWT authentication được bật | `execution-log.txt:139--153` | Scan dùng user cấu hình, nhưng không chứng minh từng endpoint được authorized. |
| Context `EPU-EOffice-API` | 271, 276, 281 | Spider và Active Scan bị giới hạn theo context. |
| OpenAPI `targetUrl/v3/api-docs`, user `dast-user` | 270--273 | Tạo tập 95 URL import; không đồng nhất với request history. |
| `scanOnlyInScope=true` | 274--275 | Passive scan chỉ quét in-scope; endpoint ngoài context không được đánh giá. |
| Spider depth 5, max duration 10 | 276--280 | Discovery bị giới hạn depth/thời gian. |
| Active Scan API policy, per-rule 5 phút, tổng 30 phút | 281--285 | Có active scan hoàn tất 19:01 (`312--313`), nhưng thiếu per-URL target/result. |
| Exit threshold High | 299 | Workflow pass chỉ cho thấy không có finding High/Critical theo gate; không nói coverage đầy đủ. |

Vì các ràng buộc scope, authentication, thời gian và API policy, run success chứng minh scan hoàn tất trong phạm vi cấu hình; không chứng minh toàn bộ endpoint hay tất cả method/role được kiểm thử.

## H. Kết luận về 101 URL

**NOT VERIFIED.** Dữ liệu gốc chứng minh 81 URL unique trong file History URL-only, không phải 101. Nó không cho phép xác định số URL thực sự được request, response, Active Scan, Spider-only, lỗi hoặc no-response. Các số 95, 103, 106 và 81 phải được báo cáo tách biệt.

## I. Dữ liệu cần bổ sung cho run sau

Workflow hiện có export job với `source = History` (`execution-log.txt:297`), nhưng chỉ export `scanned_urls.txt`. Bổ sung ngay sau Active Scan, trước upload artifact, một export HTTP History/HAR/JSON chứa ít nhất: timestamp, message ID, normalized URL, method, response status, response-present flag, source (OpenAPI/Spider/Active Scan), context/user và active-scan flag.

Ví dụ cấu hình tối thiểu (cú pháp cụ thể cần khớp phiên bản ZAP Automation Framework đang dùng) là export History sang HAR/JSON bên cạnh `scanned_urls.txt`, rồi upload cùng artifact:

```yaml
# Sau activeScan; giữ scanned_urls.txt hiện có
- type: export
  parameters:
    source: History
    fileName: /zap/wrk/reports/http-history.har
    type: har
```

Sau đó dùng script đọc HAR để tạo `url-status-summary.csv` và `url-status-details.csv`; script phải giữ request ID để một URL lặp/method khác nhau không bị gộp trước khi thống kê. Đồng thời export Spider result và Active Scan target list để phân biệt imported, discovered, requested và scanned.

