package vn.edu.epu.eoffice_be.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    void invalidDir_Empty_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setDir("");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dir".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_Blank_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setDir("   ");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dir".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_Null_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setDir(null);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dir".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_PathTraversal_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setDir("/var/eoffice/../etc/passwd");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dirValid".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_PathTraversalNormalizingToUploads_ShouldBeRejected() {
        UploadProperties props = new UploadProperties();
        props.setDir("/var/uploads/../uploads");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dirValid".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_RelativePath_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setDir("relative/uploads/documents");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dirValid".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidDir_BackslashRelativePath_ShouldBeRejected() {
        UploadProperties props = new UploadProperties();
        props.setDir("\\uploads\\docs");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "dirValid".equals(v.getPropertyPath().toString())));
    }

    @Test
    void validDir_WithoutTrailingSlash_ShouldBeValid() {
        UploadProperties props = new UploadProperties();
        props.setDir("/var/uploads");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    void validDir_WithTrailingSlash_ShouldBeValid() {
        UploadProperties props = new UploadProperties();
        props.setDir("/var/uploads/");
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidMaxSizeBytes_Zero_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(0L);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "maxSizeBytes".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidMaxSizeBytes_Negative_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(-100L);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "maxSizeBytes".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidMaxSizeBytes_ExceedsLimit_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(104857601L);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "maxSizeBytes".equals(v.getPropertyPath().toString())));
    }

    @Test
    void validMaxSizeBytes_BoundaryMinimum_ShouldBeValid() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(1L);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    void validMaxSizeBytes_BoundaryMaximum_ShouldBeValid() {
        UploadProperties props = new UploadProperties();
        props.setMaxSizeBytes(104857600L);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidAllowedExtensions_Empty_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setAllowedExtensions(Collections.emptyList());
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "allowedExtensions".equals(v.getPropertyPath().toString())));
    }

    @Test
    void invalidAllowedExtensions_Null_ShouldHaveExactViolation() {
        UploadProperties props = new UploadProperties();
        props.setAllowedExtensions(null);
        Set<ConstraintViolation<UploadProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "allowedExtensions".equals(v.getPropertyPath().toString())));
    }

    @Test
    void allowedExtensions_ShouldBeImmutable() {
        UploadProperties props = new UploadProperties();
        List<String> extensions = props.getAllowedExtensions();
        assertNotNull(extensions);
        assertThrows(UnsupportedOperationException.class, () -> extensions.add("exe"));
    }
}