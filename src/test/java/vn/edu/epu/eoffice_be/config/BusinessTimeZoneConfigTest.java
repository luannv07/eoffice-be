package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.entity.Document;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTimeZoneConfigTest {

    @AfterEach
    void tearDown() {
        Document.resetBusinessZone();
    }

    @Test
    void businessZone_ShouldUpdateDocumentBusinessZone() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "Asia/Tokyo");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Tokyo"), zone);
        assertEquals(ZoneId.of("Asia/Tokyo"), Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenNull_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", null);

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), Document.getBusinessZone());
    }

    @Test
    void businessZone_WhenEmpty_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
    }

    @Test
    void businessZone_WhenWhitespace_ShouldDefaultToHoChiMinh() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "   ");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
    }

    @Test
    void businessZone_WhenValid_ShouldUseConfiguredTimezone() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "UTC");

        ZoneId zone = config.businessZone();

        assertEquals(ZoneId.of("UTC"), zone);
    }

    @Test
    void businessZone_WhenInvalidNonBlank_ShouldFailFastWithIllegalStateException() {
        BusinessTimeZoneConfig config = new BusinessTimeZoneConfig();
        ReflectionTestUtils.setField(config, "businessTimeZone", "Invalid/Unknown_Zone");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::businessZone);
        assertTrue(ex.getMessage().contains("eoffice.timezone"));
        assertTrue(ex.getMessage().contains("Invalid/Unknown_Zone"));
    }
}