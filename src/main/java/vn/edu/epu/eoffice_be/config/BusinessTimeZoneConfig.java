package vn.edu.epu.eoffice_be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class BusinessTimeZoneConfig {

    @Value("${eoffice.timezone:Asia/Ho_Chi_Minh}")
    private String businessTimeZone;

    @Bean
    public ZoneId businessZone() {
        return ZoneId.of(businessTimeZone != null ? businessTimeZone : "Asia/Ho_Chi_Minh");
    }
}
