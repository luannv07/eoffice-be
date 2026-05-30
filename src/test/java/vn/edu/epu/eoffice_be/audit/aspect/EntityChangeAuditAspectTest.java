package vn.edu.epu.eoffice_be.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Table;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import vn.edu.epu.eoffice_be.audit.repository.ChangeHistoryLogRepository;
import vn.edu.epu.eoffice_be.common.annotation.TrackChange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityChangeAuditAspectTest {

    private final ChangeHistoryLogRepository repository = mock(ChangeHistoryLogRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);
    private final PersistenceUnitUtil putil = mock(PersistenceUnitUtil.class);
    private final EntityChangeAuditAspect aspect = new EntityChangeAuditAspect(repository, new ObjectMapper());

    EntityChangeAuditAspectTest() {
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
        ReflectionTestUtils.setField(aspect, "sourceService", "eoffice-be");
        when(entityManager.getEntityManagerFactory()).thenReturn(emf);
        when(emf.getPersistenceUnitUtil()).thenReturn(putil);
    }

    @AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipSaveForNonTrackableEntity() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Object entity = new Object();
        when(joinPoint.proceed()).thenReturn(entity);

        Object result = aspect.auditSave(joinPoint, entity);
        assertEquals(entity, result);
        verify(repository, never()).save(any(ChangeHistoryLog.class));
    }

    @Test
    void shouldWriteCreateHistoryForTrackableEntity() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        TrackableDemo entity = new TrackableDemo();
        entity.setName("v1");

        when(putil.getIdentifier(entity)).thenReturn(null, 10L);
        when(joinPoint.proceed()).thenReturn(entity);

        aspect.auditSave(joinPoint, entity);
        verify(repository).save(any(ChangeHistoryLog.class));
    }

    @Test
    void shouldWriteUpdateHistoryForTrackableEntity() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        TrackableDemo before = new TrackableDemo();
        before.setName("old");
        TrackableDemo after = new TrackableDemo();
        after.setName("new");

        when(putil.getIdentifier(after)).thenReturn(10L, 10L);
        when(entityManager.find(TrackableDemo.class, 10L)).thenReturn(before);
        when(joinPoint.proceed()).thenReturn(after);

        aspect.auditSave(joinPoint, after);
        verify(repository).save(any(ChangeHistoryLog.class));
    }

    @Test
    void shouldWriteDeleteHistoryForTrackableEntity() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        TrackableDemo entity = new TrackableDemo();
        entity.setName("deleted");

        when(putil.getIdentifier(entity)).thenReturn(22L);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.auditDelete(joinPoint, entity);
        verify(repository).save(any(ChangeHistoryLog.class));
    }

    @TrackChange
    @Table(name = "demo_table")
    static class TrackableDemo {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
