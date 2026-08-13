package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorsConfigTest {

    @Test
    void corsFilterShouldBeCreated() {
        CorsConfig config = new CorsConfig(new JwtProperties());
        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);
    }
}
