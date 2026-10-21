package vn.edu.epu.eoffice_be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
	private static final String SECURITY_SCHEME_NAME = "bearerAuth";

	@Bean
	public OpenAPI eofficeOpenApi() {
		return new OpenAPI()
						.info(new Info()
										.title("eOffice Backend API")
										.description("API documentation for eOffice DevSecOps staging environment")
										.version("v1")
										.contact(new Contact()
														.name("eOffice DevSecOps Team"))
										.license(new License()
														// OpenAPI 3.1 (what springdoc emits) requires info.license.identifier
														// next to info.license.name - without it strict parsers such as the
														// one used by OWASP ZAP reject the whole definition.
														.name("Internal Use Only")
														.identifier("LicenseRef-Internal-Use-Only")))
						.components(new Components()
										.addSecuritySchemes(
														SECURITY_SCHEME_NAME,
														new SecurityScheme()
																		.name("Authorization")
																		.type(SecurityScheme.Type.HTTP)
																		.scheme("bearer")
																		.bearerFormat("JWT")
										)
						)
						.addSecurityItem(
										new SecurityRequirement()
														.addList(SECURITY_SCHEME_NAME)
						);
	}
}
