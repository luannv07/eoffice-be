package vn.edu.epu.eoffice_be.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.edu.epu.eoffice_be.entity.Document;

import java.time.DateTimeException;
import java.time.ZoneId;

@Slf4j
@Configuration
public class BusinessTimeZoneConfig {

    @Value("${eoffice.timezone:Asia/Ho_Chi_Minh}")
    private String businessTimeZone;

    @Bean
    public ZoneId businessZone() {
        String trimmed = businessTimeZone != null ? businessTimeZone.trim() : "";
        ZoneId zone;
        if (trimmed.isEmpty()) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        } else {
            try {
                zone = ZoneId.of(trimmed);
            } catch (DateTimeException ex) {
                log.error("Cấu hình eoffice.timezone không hợp lệ: '{}'", trimmed, ex);
                throw new IllegalStateException("Cấu hình eoffice.timezone không hợp lệ: '" + trimmed + "'", ex);
            }
        }
        Document.setBusinessZone(zone);
        return zone;
    }
}

