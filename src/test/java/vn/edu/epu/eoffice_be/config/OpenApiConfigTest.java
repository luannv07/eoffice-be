package vn.edu.epu.eoffice_be.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    @Test
    void eofficeOpenApiShouldContainExpectedMetadata() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.eofficeOpenApi();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("eOffice Backend API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
        assertEquals("eOffice DevSecOps Team", openAPI.getInfo().getContact().getName());
        assertEquals("Internal Use Only", openAPI.getInfo().getLicense().getName());
    }

    @Test
    void eofficeOpenApiShouldContainDescription() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.eofficeOpenApi();
        assertTrue(openAPI.getInfo().getDescription().contains("eOffice"));
    }

    @Test
    void eofficeOpenApiShouldContainNonNullContactAndLicense() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.eofficeOpenApi();
        assertNotNull(openAPI.getInfo().getContact());
        assertNotNull(openAPI.getInfo().getLicense());
    }
}
