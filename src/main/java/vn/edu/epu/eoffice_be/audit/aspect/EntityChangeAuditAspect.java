package vn.edu.epu.eoffice_be.audit.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import vn.edu.epu.eoffice_be.audit.repository.ChangeHistoryLogRepository;
import vn.edu.epu.eoffice_be.common.annotation.TrackChange;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EntityChangeAuditAspect {

    private final ChangeHistoryLogRepository changeHistoryLogRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${spring.application.name:eoffice-be}")
    private String sourceService;

    @Around("execution(* org.springframework.data.repository.CrudRepository+.save(..)) && args(entity)")
    @Transactional
    public Object auditSave(ProceedingJoinPoint joinPoint, Object entity) throws Throwable {
        if (!isTrackable(entity)) {
            return joinPoint.proceed();
        }

        Object entityId = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        boolean isCreate = entityId == null;
        String beforeData = null;

        if (!isCreate) {
            Object currentEntity = entityManager.find(entity.getClass(), entityId);
            beforeData = toJson(currentEntity);
        }

        Object result = joinPoint.proceed();
        Object savedEntity = result != null ? result : entity;
        Object savedEntityId = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(savedEntity);
        String afterData = toJson(savedEntity);

        writeHistoryLog(
                savedEntity,
                savedEntityId,
                isCreate ? "CREATE" : "UPDATE",
                beforeData,
                afterData
        );
        return result;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.delete(..)) && args(entity)")
    @Transactional
    public Object auditDelete(ProceedingJoinPoint joinPoint, Object entity) throws Throwable {
        if (!isTrackable(entity)) {
            return joinPoint.proceed();
        }

        Object entityId = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        String beforeData = toJson(entity);
        Object result = joinPoint.proceed();
        writeHistoryLog(entity, entityId, "DELETE", beforeData, null);
        return result;
    }

    private void writeHistoryLog(Object entity, Object objectId, String action, String beforeData, String afterData) {
        String changedFields = buildChangedFields(beforeData, afterData);
        ChangeHistoryLog logEntry = ChangeHistoryLog.builder()
                .tableName(resolveTableName(entity.getClass()))
                .objectId(String.valueOf(objectId))
                .action(action)
                .changedBy(resolveUsername())
                .changedAt(Instant.now())
                .sourceService(sourceService)
                .beforeData(beforeData)
                .afterData(afterData)
                .changedFields(changedFields)
                .build();
        changeHistoryLogRepository.save(logEntry);
    }

    private boolean isTrackable(Object entity) {
        return entity != null && entity.getClass().isAnnotationPresent(TrackChange.class);
    }

    private String resolveTableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        return table != null && !table.name().isBlank() ? table.name() : entityClass.getSimpleName();
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return Optional.ofNullable(authentication.getName()).orElse("system");
    }

    private String toJson(Object input) {
        if (input == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to convert entity to json for change history", ex);
            return String.valueOf(input);
        }
    }

    private String buildChangedFields(String beforeData, String afterData) {
        if (beforeData == null || afterData == null) {
            return null;
        }
        try {
            JsonNode beforeNode = objectMapper.readTree(beforeData);
            JsonNode afterNode = objectMapper.readTree(afterData);
            List<String> changed = new ArrayList<>();
            Iterator<String> fieldNames = afterNode.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                JsonNode beforeValue = beforeNode.get(field);
                JsonNode afterValue = afterNode.get(field);
                if (!Objects.equals(beforeValue, afterValue)) {
                    changed.add(field);
                }
            }
            return objectMapper.writeValueAsString(changed);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to build changedFields", ex);
            return null;
        }
    }
}
