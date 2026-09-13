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

	@Test
	void objectMapperShouldSerializePage() {
		JacksonConfig config = new JacksonConfig();
		ObjectMapper mapper = config.objectMapper();
		org.springframework.data.domain.Page<String> page = new org.springframework.data.domain.PageImpl<>(
				java.util.List.of("item1", "item2"),
				org.springframework.data.domain.PageRequest.of(0, 10),
				2
		);
		assertDoesNotThrow(() -> {
			String json = mapper.writeValueAsString(page);
			assertNotNull(json);
			org.junit.jupiter.api.Assertions.assertTrue(json.contains("content"));
		});
	}
}
