package vn.edu.epu.eoffice_be.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
		Page<String> page = new PageImpl<>(
				List.of("item1", "item2"),
				PageRequest.of(0, 10),
				2
		);
		assertDoesNotThrow(() -> {
			String json = mapper.writeValueAsString(page);
			assertNotNull(json);
			JsonNode root = mapper.readTree(json);
			assertTrue(root.has("content"));
			assertTrue(root.get("content").isArray());
			assertEquals(2, root.get("content").size());
			assertEquals("item1", root.get("content").get(0).asText());
			assertEquals("item2", root.get("content").get(1).asText());
			assertTrue(root.has("totalElements"));
			assertEquals(2, root.get("totalElements").asInt());
			assertTrue(root.has("totalPages"));
			assertEquals(1, root.get("totalPages").asInt());
			assertTrue(root.has("pageable"));
		});
	}
}
