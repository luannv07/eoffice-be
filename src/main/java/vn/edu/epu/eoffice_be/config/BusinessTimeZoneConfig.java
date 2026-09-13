package vn.edu.epu.eoffice_be.config;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.epu.eoffice_be.entity.Document;

import java.time.ZoneId;

@Configuration
public class BusinessTimeZoneConfig {

    @Value("${eoffice.timezone:Asia/Ho_Chi_Minh}")
    private String businessTimeZone;

    @Bean
    public ZoneId businessZone() {
        String trimmed = businessTimeZone != null ? businessTimeZone.trim() : "";
        ZoneId zone = trimmed.isEmpty() ? ZoneId.of("Asia/Ho_Chi_Minh") : ZoneId.of(trimmed);
        Document.setBusinessZone(zone);
        return zone;
    }
}

