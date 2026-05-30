package vn.edu.epu.eoffice_be.audit.service;

import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.repository.UserAuditLogRepository;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogAsyncServiceTest {

    @Test
    void saveAsyncShouldPersistLog() {
        UserAuditLogRepository repository = mock(UserAuditLogRepository.class);
        AuditLogAsyncService service = new AuditLogAsyncService(repository);
        UserAuditLog log = UserAuditLog.builder().username("u").build();
        service.saveAsync(log);
        verify(repository).save(log);
    }

    @Test
    void saveAsyncShouldSwallowExceptionAndNotThrow() {
        UserAuditLogRepository repository = mock(UserAuditLogRepository.class);
        AuditLogAsyncService service = new AuditLogAsyncService(repository);
        UserAuditLog log = UserAuditLog.builder().username("u").build();
        doThrow(new RuntimeException("db-down")).when(repository).save(log);
        service.saveAsync(log);
        verify(repository).save(log);
    }
}
