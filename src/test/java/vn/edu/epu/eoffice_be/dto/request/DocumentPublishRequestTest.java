package vn.edu.epu.eoffice_be.dto.request;

import vn.edu.epu.eoffice_be.common.enums.DistributionScope;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPublishRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void missingDistributionScopeIsInvalid() {
        DocumentPublishRequest request = DocumentPublishRequest.builder().build();
        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("distributionScope")));
    }

    @Test
    void recipientIdsMustBePositiveAndNonNull() {
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .distributionScope(DistributionScope.MULTIPLE_ORGS)
                .recipientOrgIds(java.util.Arrays.asList(null, 0L, -1L)).build();
        assertEquals(3, validator.validate(request).size());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi cả issueDate và effectiveDate đều null")
    void testDates_WhenBothNull_IsValid() {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(null)
                .effectiveDate(null)
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
        assertTrue(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi issueDate có giá trị và effectiveDate null")
    void testDates_WhenEffectiveDateNull_IsValid() {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(Instant.now())
                .effectiveDate(null)
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
        assertTrue(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi issueDate null và effectiveDate có giá trị")
    void testDates_WhenIssueDateNull_IsValidInBeanValidation() {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(null)
                .effectiveDate(Instant.now())
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
        assertTrue(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi effectiveDate bằng đúng issueDate")
    void testDates_WhenEffectiveDateEqualsIssueDate_IsValid() {
        Instant now = Instant.now();
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(now)
                .effectiveDate(now)
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
        assertTrue(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi effectiveDate sau issueDate")
    void testDates_WhenEffectiveDateAfterIssueDate_IsValid() {
        Instant now = Instant.now();
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(now)
                .effectiveDate(now.plus(5, ChronoUnit.DAYS))
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
        assertTrue(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Không hợp lệ khi effectiveDate trước issueDate")
    void testDates_WhenEffectiveDateBeforeIssueDate_IsInvalid() {
        Instant now = Instant.now();
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(now)
                .effectiveDate(now.minus(1, ChronoUnit.DAYS))
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "effectiveDateValid".equals(v.getPropertyPath().toString())));
        assertFalse(request.isEffectiveDateValid());
    }

    @Test
    @DisplayName("DocumentPublishRequest: Hợp lệ khi bookId null (cho phép kế thừa số hiệu đã cấp)")
    void testBookId_WhenNull_IsValid() {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .issueDate(Instant.now())
                .effectiveDate(Instant.now())
                .build();

        Set<ConstraintViolation<DocumentPublishRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
}
