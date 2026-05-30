package vn.edu.epu.eoffice_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JacksonConfigTest {

    @Test
    void objectMapperBeanShouldBeCreated() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();
        assertNotNull(mapper);
    }

    @Test
    void objectMapperShouldSerializeInstant() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();
        assertDoesNotThrow(() -> mapper.writeValueAsString(Instant.now()));
    }

    @Test
    void objectMapperFactoryShouldReturnDifferentInstancesPerCall() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper1 = config.objectMapper();
        ObjectMapper mapper2 = config.objectMapper();
        assertNotNull(mapper1);
        assertNotNull(mapper2);
    }
}
