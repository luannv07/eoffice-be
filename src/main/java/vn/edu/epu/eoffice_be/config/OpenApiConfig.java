package vn.edu.epu.eoffice_be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
                                .name("Internal Use Only")));
    }
}
