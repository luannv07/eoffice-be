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
    void invalidDir_ShouldHaveViolationsWithPropertyPath() {
        UploadProperties props = new UploadProperties();

        // Rỗng
        props.setDir("");
        Set<ConstraintViolation<UploadProperties>> v1 = validator.validate(props);
        assertTrue(v1.stream().anyMatch(v -> v.getPropertyPath().toString().contains("dir")));

        // Khoảng trắng
        props.setDir("   ");
        Set<ConstraintViolation<UploadProperties>> v2 = validator.validate(props);
        assertTrue(v2.stream().anyMatch(v -> v.getPropertyPath().toString().contains("dir")));

        // Null
        props.setDir(null);
        Set<ConstraintViolation<UploadProperties>> v3 = validator.validate(props);
        assertTrue(v3.stream().anyMatch(v -> v.getPropertyPath().toString().contains("dir")));

        // Path traversal chứa ..
        props.setDir("/var/eoffice/../etc/passwd");
        Set<ConstraintViolation<UploadProperties>> v4 = validator.validate(props);
        assertTrue(v4.stream().anyMatch(v -> v.getPropertyPath().toString().contains("dir")));

        // Đường dẫn tương đối
        props.setDir("relative/uploads/documents");
        Set<ConstraintViolation<UploadProperties>> v5 = validator.validate(props);
        assertTrue(v5.stream().anyMatch(v -> v.getPropertyPath().toString().contains("dir")));
    }

    @Test
    void invalidMaxSizeBytes_ShouldHaveViolationsWithPropertyPath() {
        UploadProperties props = new UploadProperties();

        // 0
        props.setMaxSizeBytes(0L);
        Set<ConstraintViolation<UploadProperties>> v1 = validator.validate(props);
        assertTrue(v1.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maxSizeBytes")));

        // Âm
        props.setMaxSizeBytes(-100L);
        Set<ConstraintViolation<UploadProperties>> v2 = validator.validate(props);
        assertTrue(v2.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maxSizeBytes")));

        // Vượt quá giới hạn trên (100 MB = 104857600L)
        props.setMaxSizeBytes(104857601L);
        Set<ConstraintViolation<UploadProperties>> v3 = validator.validate(props);
        assertTrue(v3.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maxSizeBytes")));
    }

    @Test
    void invalidAllowedExtensions_ShouldHaveViolationsWithPropertyPath() {
        UploadProperties props = new UploadProperties();

        // Danh sách rỗng
        props.setAllowedExtensions(Collections.emptyList());
        Set<ConstraintViolation<UploadProperties>> v1 = validator.validate(props);
        assertTrue(v1.stream().anyMatch(v -> v.getPropertyPath().toString().contains("allowedExtensions")));

        // Null
        props.setAllowedExtensions(null);
        Set<ConstraintViolation<UploadProperties>> v2 = validator.validate(props);
        assertTrue(v2.stream().anyMatch(v -> v.getPropertyPath().toString().contains("allowedExtensions")));
    }

    @Test
    void allowedExtensions_ShouldBeImmutable() {
        UploadProperties props = new UploadProperties();
        List<String> extensions = props.getAllowedExtensions();
        assertNotNull(extensions);
        assertThrows(UnsupportedOperationException.class, () -> extensions.add("exe"));
    }
}