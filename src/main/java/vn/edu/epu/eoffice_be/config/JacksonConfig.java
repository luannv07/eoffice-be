package vn.edu.epu.eoffice_be.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;

@Configuration
public class JacksonConfig {

	@Bean
	public Module pageableJacksonModule() {
		return new SpringDataJacksonConfiguration().pageModule();
	}

	public ObjectMapper objectMapper() {
		return new ObjectMapper()
				.findAndRegisterModules()
				.registerModule(new SpringDataJacksonConfiguration().pageModule());
	}
}
