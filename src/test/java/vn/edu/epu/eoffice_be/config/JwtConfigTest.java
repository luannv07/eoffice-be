package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigTest {

	private final JwtConfig jwtConfig = new JwtConfig();

	@Test
	void blankSecretShouldFailFast() {
		JwtProperties properties = new JwtProperties();
		properties.setSecretKey("   ");

		assertThrows(IllegalStateException.class, () -> jwtConfig.jwtSecretKey(properties));
	}

	@Test
	void validSecretShouldProduceHmacSha256Key() {
		JwtProperties properties = new JwtProperties();
		properties.setSecretKey("eoffice-be-jwt-secret-fixed-for-all-environments-2026");

		SecretKey key = jwtConfig.jwtSecretKey(properties);

		assertTrue(key.getAlgorithm().startsWith("HmacSHA"));
	}
}
