package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

/** Reads existing audit data without entity soft-delete filters or any schema changes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PkiSigningHistory {
    private final JdbcTemplate jdbcTemplate;

    public boolean hasAnySigningHistory() {
        return exists("SELECT EXISTS (SELECT 1 FROM document_workflow_logs WHERE NULLIF(TRIM(signature_hash), '') IS NOT NULL)")
                || exists("SELECT EXISTS (SELECT 1 FROM documents WHERE NULLIF(TRIM(official_hash), '') IS NOT NULL OR NULLIF(TRIM(official_file_path), '') IS NOT NULL)")
                || exists("SELECT EXISTS (SELECT 1 FROM document_process_logs WHERE action = 'SUBMIT_APPROVAL' AND note LIKE '%[Đã ký nháy tự động, SHA256:%')");
    }

    public boolean hasUserSigningHistory(String userId) {
        final long id;
        try {
            id = Long.parseLong(userId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PKI_USER_KEYSTORE_INVALID, "Định danh người ký không hợp lệ");
        }
        return exists("SELECT EXISTS (SELECT 1 FROM document_workflow_logs WHERE actor_id = ? AND NULLIF(TRIM(signature_hash), '') IS NOT NULL)", id)
                || exists("SELECT EXISTS (SELECT 1 FROM documents WHERE signer_id = ? AND (NULLIF(TRIM(official_hash), '') IS NOT NULL OR NULLIF(TRIM(official_file_path), '') IS NOT NULL))", id)
                || exists("SELECT EXISTS (SELECT 1 FROM document_process_logs WHERE actor_user_id = ? AND action = 'SUBMIT_APPROVAL' AND note LIKE '%[Đã ký nháy tự động, SHA256:%')", id);
    }

    private boolean exists(String sql, Object... parameters) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, parameters));
    }
}
