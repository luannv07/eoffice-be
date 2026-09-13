package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.entity.Document;

import java.time.DateTimeException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTimeZoneConfigTest {

    @AfterEach
    void tearDown() {
        Document.setBusinessZone(Document.DEFAULT_BUSINESS_ZONE);
    }

    @Test
    void businessZone_WhenNull_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", null);

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
        assertEquals(zone, Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenEmpty_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
        assertEquals(zone, Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenWhitespace_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "   ");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
        assertEquals(zone, Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenValid_ShouldUseConfiguredTimezoneAndWireToDocument() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "UTC");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("UTC"), zone);
        assertEquals(ZoneId.of("UTC"), Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenInvalidNonBlank_ShouldFailFast() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "Invalid/Unknown_Zone");

        assertThrows(DateTimeException.class, config::businessZone);
    }
}