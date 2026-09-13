package vn.edu.epu.eoffice_be.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UploadPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void defaultProperties_ShouldBeValid() {
        UploadProperties props = new UploadProperties();
        var violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidDir_ShouldHaveViolations() {
        UploadProperties props = new UploadProperties();
        props.setDir("");
        assertFalse(validator.validate(props).isEmpty());

        props.setDir("   ");
        assertFalse(validator.validate(props).isEmpty());

        props.setDir(null);
        assertFalse(validator.validate(props).isEmpty());
    }

    @Test
    void invalidMaxSizeBytes_ShouldHaveViolations() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(0L);
        assertFalse(validator.validate(props).isEmpty());

        props.setMaxSizeBytes(-100L);
        assertFalse(validator.validate(props).isEmpty());
    }

    @Test
    void invalidAllowedExtensions_ShouldHaveViolations() {
        UploadProperties props = new UploadProperties();
        props.setAllowedExtensions(Collections.emptyList());
        assertFalse(validator.validate(props).isEmpty());

        props.setAllowedExtensions(null);
        assertFalse(validator.validate(props).isEmpty());
    }

    @Test
    void allowedExtensions_ShouldBeImmutable() {
        UploadProperties props = new UploadProperties();
        List<String> extensions = props.getAllowedExtensions();
        assertNotNull(extensions);
        assertThrows(UnsupportedOperationException.class, () -> extensions.add("exe"));
    }
}