package vn.edu.epu.eoffice_be.service;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
/** Narrow boundary guard; a separate test registry is outside phase A. */
public final class TestDocumentGuard {
    private TestDocumentGuard() { }
    public static void assertReal(Document document) {
        if (document != null && document.isTestDocument())
            throw new BusinessException(ErrorCode.FORBIDDEN, "Hồ sơ mô phỏng không được cấp số hoặc phát hành vào sổ thật.");
    }
}
