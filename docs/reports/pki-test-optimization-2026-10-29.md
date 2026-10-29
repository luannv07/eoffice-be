# Tối ưu 5 lớp test PKI — 2026-10-29

Trạng thái: **Hoàn thành — review đã thông qua**. Người dùng xác nhận “pass review” ngày 2026-10-29. Kết quả kiểm thử đã ghi nhận bên dưới; xác nhận review không phát sinh thay đổi code.

## Phạm vi và cách triển khai

Chỉ thay 5 lớp test, thêm helper/test hỗ trợ và tài liệu; không thay `src/main`, `pom.xml`, cấu hình ứng dụng/CI, migration hoặc database thật. Không commit/push. Giữ nguyên toàn bộ assertion cũ; chỉ bổ sung kiểm tra hoặc thay bước chuẩn bị dữ liệu.

- `FastPkiTest`/`PkiIterationExtension` đặt `org.bouncycastle.pkcs12.store_it_count=1` trong phạm vi lớp test: PBE khóa/chứng thư 1, MAC 2. Có ResourceLock, Isolated và chạy cùng thread để không rò cấu hình sang lớp khác; các thread bên trong ca đồng thời vẫn hoạt động. Property được phục hồi và JUnit store cũng đóng scope khi setup/teardown lỗi.
- `Pkcs12TestSupportTest` có 6 ca: ASN.1 1/1/2; đúng/sai mật khẩu và dữ liệu hỏng; đổi property giữa các lần store; mặc định 600000/600000/1200000 và đọc lại thật; thread con; phục hồi sau lỗi; cô lập bundle và bản ghi mẫu. Hai nội dung cuối được chia ở hai hàm riêng; tổng chính xác 6 hàm test.
- `PkiTestSamples` cache keypair/material/PKCS12 mẫu trong tiến trình. Khóa và chứng thư được sao chép từ encoding; chain, entity, byte[] và repository riêng theo ca. PKCS12 mẫu được mã hóa bằng PkiEncryptionService thật; AAD hiện tại gắn key-id. Chứng thư có thời gian tính từ lượt chạy. Không đọc khóa triển khai.
- `PkiSampleFixtures` là opt-in; fixture cũ không đổi cho các lớp ngoài phạm vi. Test user dùng CA bundle mẫu khi CA không phải đối tượng kiểm tra. Test CA hỏng vẫn dùng service CA thật.
- Lifecycle: lưu/đọc khóa, sai mật khẩu, chứng thư/khóa sai, issuer/UID, PEM/serial và hết hạn vẫn chạy validation thật. Các ca hỏng có đối chứng dữ liệu hợp lệ và kiểm tra lớp lỗi AES/PKCS12, không giảm hai kiểm tra ensure/require.
- CA service: khởi tạo và khởi tạo đồng thời vẫn thật, giữ assertion RSA 3072. Ca bản ghi có sẵn nhận template; diagnostic được kiểm tra cụ thể.
- Repository: H2 UUID, Liquibase, transaction và concurrency thật. CA mẫu được requireValidCa thật xác nhận; unique/FK nhận bản ghi mẫu, không cấp khóa để chuẩn bị.
- Signer/verifier: bộ cấp khóa là InMemorySigningFixture, chỉ trả khóa đã mở; không tồn tại dependency thật để đọc PKCS12. Test double đếm truy cập và báo lỗi khi gọi đường CA riêng ngoài dự kiến. Signer/verifier, chữ ký CMS, DocMDP, thu hồi và khóa file vẫn thật. Không mock kết quả chữ ký.

## Mốc so sánh 56 ca

Cùng máy, Java 21.0.11, Maven offline, cùng Byte Buddy agent 1.15.11, cùng lệnh `clean test` và danh sách 5 lớp; không chạy benchmark/tác vụ test khác song song. Thời gian Maven gồm clean, compile và chuẩn bị. Đây là phép đo máy hiện tại, không phải cam kết thời gian trên CI/máy người dùng.

| Lớp | Ca | Trước (giây) | Sau (giây) |
|---|---:|---:|---:|
| `PdfSignerLifecycleTest` | 24 | 92.010 | 0.897 |
| `PdfSignerTest` | 4 | 30.710 | 0.183 |
| `PkiCaServiceTest` | 20 | 28.540 | 1.549 |
| `PdfSignatureVerifierTest` | 5 | 21.500 | 1.876 |
| `PkiUserKeystoreRepositoryTest` | 3 | 24.050 | 6.567 |
| Tổng thời gian các lớp | 56 | 196.810 | 11.072 |

- Tổng Maven: **03:31 phút → 26.483 giây**, giảm khoảng **87.45%**. Cả hai lượt 56 ca, 0 failure/error/skipped.
- Thời gian từng lớp phụ thuộc thứ tự/JIT và chi phí tạo mẫu. Không cộng các lượt chạy riêng để suy ra thời gian toàn suite.
- Lifecycle theo giai đoạn: gốc 92.010s → chỉ giảm iteration 6.139s → thêm template 3.298s (chạy riêng). Trong nhóm cuối, lớp chạy sau các lớp đã warm-up nên còn 0.897s; không xem đây là cùng điều kiện với chạy riêng.
- Giai đoạn B: 23 ca đạt; repository 7.264s, CA 2.006s, Maven 12.909s.
- Giai đoạn C: 9 ca đạt; verifier 2.801s, signer 0.245s, Maven 6.567s.

## Benchmark codec

Cùng một cặp RSA-2048 và chứng thư synthetic dạng DER cho cả sáu JVM. Các file synthetic chỉ dưới `target/pki-benchmark-synthetic-only`, không phải khóa triển khai. Mỗi cấu hình có 3 JVM mới; trong từng JVM warm-up một cặp encode/decode rồi đo 3 cặp. Báo trung vị của thời gian trung bình từng JVM; không có lượt bị loại bỏ. Cùng agent, thuật toán và password test. Thời gian tạo/đọc mẫu và khởi động JVM không tính vào codec, nhưng tổng Maven ở trên vẫn tính toàn bộ startup/setup.

| Cấu hình | Encode trung vị (ms) | Decode trung vị (ms) |
|---|---:|---:|
| Provider mặc định | 696.282 | 703.235 |
| Test 1/1/2 | 1.075 | 1.823 |

Có benchmark thăm dò dùng mẫu mới mỗi JVM trước đó, nhưng không dùng nó làm kết quả chính vì khác byte đầu vào. Kết quả chính ở `pki-optimization-codec-shared-benchmark.json`.

## Xác minh độc lập và hồi quy

- Chạy riêng nhóm corruptUserRecord (8 biến thể) cùng 6 test helper: 14 ca đạt, không cần ca cấp lần đầu chạy trước.
- Chạy riêng ca khởi tạo CA/đọc lại và ca lock thu hồi: 2 ca đạt.
- Toàn suite lần đầu: **1391 ca, 0 failure, 0 error, 2 skipped, BUILD SUCCESS**, Maven **50.935s**. Tăng 6 ca helper so với 1385; số skipped không tăng.
- Lượt toàn suite sau cùng trên code cuối: **1391 ca, 0 failure, 0 error, 2 skipped, BUILD SUCCESS**, Maven **56.580s**. Lần trước 50.935s; dùng 56.580s làm số kết luận cuối, không chọn lượt nhanh nhất.
- `git diff --check` đạt; đối chiếu assertion/verify cũ ở cả 5 lớp không có dòng assertion nào bị loại bỏ.
- Production không có cache khóa hoặc cấu hình iteration thấp mới. Vẫn có test encode/decode thực với cấu hình mặc định.

## Log và cách chạy lại

Log nằm ở `D:/code/e-office/`:

- `pki-optimization-baseline-clean-retry.log`: mốc sạch thành công.
- `pki-optimization-stage-a-iterations.log`, `pki-optimization-stage-a-samples.log`: tách hiệu quả iteration/template.
- `pki-optimization-stage-b.log`, `pki-optimization-stage-c-memory.log`.
- `pki-optimization-final-56.log`: nhóm 56 ca sau tối ưu từ build sạch.
- `pki-optimization-codec-shared-benchmark.log` và `.json`: sáu JVM, cùng mẫu.
- `pki-optimization-independent-lifecycle.log`, `pki-optimization-independent-ca-verifier.log`.
- `pki-optimization-full-suite.log`, `pki-optimization-full-final.log`.

Lệnh nhóm:

```powershell
mvn -o -B "-DargLine=-javaagent:C:\Users\admin\.m2\repository\net\bytebuddy\byte-buddy-agent\1.15.11\byte-buddy-agent-1.15.11.jar" "-Dtest=PdfSignerLifecycleTest,PkiCaServiceTest,PkiUserKeystoreRepositoryTest,PdfSignerTest,PdfSignatureVerifierTest" clean test
```

Chạy toàn bộ dùng cùng lệnh, bỏ `-Dtest=...` và bỏ `clean` nếu không cần build sạch. Đường dẫn agent là của máy đo, cần điều chỉnh nếu Maven cache ở nơi khác.

Benchmark có entrypoint `Pkcs12TestSupportTest.main` ở test scope. Lấy classpath từ property `java.class.path` trong Surefire XML. Gọi `prepare <thư-mục-target>` một lần, rồi `default <thư-mục>` và `fast <thư-mục>` ở các JVM mới, lặp ba lần. Không cấu hình property thấp ngoài extension/entrypoint test.

## Các lượt không dùng làm mốc

- Lượt đầu có NoClassDefFoundError ở PkiFileSupport$IOCallable, không dùng tính mức cải thiện. File class có mặt khi kiểm tra sau lỗi; chưa xác định nguyên nhân gốc. Build sạch chạy lại thành công, không sửa production.
- Lượt compile helper đầu lỗi do CloseableResource không kế thừa AutoCloseable ở dependency hiện tại; đã bổ sung interface, không bỏ test.
- Script chỉnh source có tiếng Việt từng lỗi cp1252 trước khi sửa file; chạy lại bằng UTF-8. Lượt C bắt đầu sau script lỗi không phải kết quả tối ưu.

Các mục lessons được bổ sung tại `D:/code/e-office/docs/lessons.md`; giữ nguyên nội dung cũ.
