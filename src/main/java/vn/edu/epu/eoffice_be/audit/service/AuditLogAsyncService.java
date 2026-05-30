package vn.edu.epu.eoffice_be.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.repository.UserAuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogAsyncService {

    private final UserAuditLogRepository userAuditLogRepository;

    @Async("auditTaskExecutor")
    public void saveAsync(UserAuditLog userAuditLog) {
        try {
            userAuditLogRepository.save(userAuditLog);
        } catch (Exception ex) {
            log.error("Failed to persist user audit log asynchronously", ex);
        }
    }
}
