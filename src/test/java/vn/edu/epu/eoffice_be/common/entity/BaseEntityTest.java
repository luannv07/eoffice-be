package vn.edu.epu.eoffice_be.common.entity;

import jakarta.persistence.EntityListeners;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    @Test
    void baseEntityShouldContainAuditingFields() throws NoSuchFieldException {
        assertNotNull(BaseEntity.class.getDeclaredField("createdBy"));
        assertNotNull(BaseEntity.class.getDeclaredField("createdDate"));
        assertNotNull(BaseEntity.class.getDeclaredField("updatedBy"));
        assertNotNull(BaseEntity.class.getDeclaredField("updatedDate"));
    }

    @Test
    void baseEntityShouldHaveAuditingEntityListener() {
        EntityListeners listeners = BaseEntity.class.getAnnotation(EntityListeners.class);
        assertNotNull(listeners);
        assertEquals(1, listeners.value().length);
        assertTrue(listeners.value()[0].equals(AuditingEntityListener.class));
    }
}
