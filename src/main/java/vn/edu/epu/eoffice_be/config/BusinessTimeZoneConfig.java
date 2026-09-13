package vn.edu.epu.eoffice_be.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import vn.edu.epu.eoffice_be.entity.Document;

import java.time.ZoneId;

@Configuration
public class BusinessTimeZoneConfig {

    @Value("${eoffice.timezone:Asia/Ho_Chi_Minh}")
    private String businessTimeZone;

    @PostConstruct
    public void init() {
        Document.setBusinessZone(ZoneId.of(businessTimeZone));
    }
}
