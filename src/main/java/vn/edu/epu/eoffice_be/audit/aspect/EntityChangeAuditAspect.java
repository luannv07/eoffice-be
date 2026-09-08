package vn.edu.epu.eoffice_be.audit.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import vn.edu.epu.eoffice_be.audit.repository.ChangeHistoryLogRepository;
import vn.edu.epu.eoffice_be.common.annotation.TrackChange;
import vn.edu.epu.eoffice_be.common.annotation.TrackField;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            beforeData = toJson(toAuditMap(currentEntity));
        }

        Object result = joinPoint.proceed();
        Object savedEntity = result != null ? result : entity;
        Object savedEntityId = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(savedEntity);
        String afterData = toJson(toAuditMap(savedEntity));

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
        String beforeData = toJson(toAuditMap(entity));
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
            try {
                return objectMapper.writeValueAsString(java.util.Map.of("raw", String.valueOf(input)));
            } catch (JsonProcessingException ignored) {
                return "{\"raw\":\"serialization_error\"}";
            }
        }
    }

    /**
     * Build a flat map of the entity's field values, honoring {@link TrackField}:
     * ignored fields are skipped, and a non-blank label is used as the key instead of the field name.
     * Association fields (entity-typed) are skipped to avoid lazy loading and circular serialization.
     */
    private Map<String, Object> toAuditMap(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                TrackField trackField = field.getAnnotation(TrackField.class);
                if (trackField != null && trackField.ignore()) {
                    continue;
                }
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(entity);
                } catch (IllegalAccessException ex) {
                    log.warn("Failed to read field {} for change history", field.getName(), ex);
                    continue;
                }
                if (value != null && value.getClass().isAnnotationPresent(Entity.class)) {
                    continue;
                }
                String key = (trackField != null && !trackField.label().isBlank())
                        ? trackField.label()
                        : field.getName();
                map.put(key, value);
            }
            clazz = clazz.getSuperclass();
        }
        return map;
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
