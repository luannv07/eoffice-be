package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DocumentReadableDraftsGuardTest {
    static Stream<Arguments> invalidPolicies() {
        return Stream.of(false,true).flatMap(legacy -> Stream.of(false,true).flatMap(admin -> Stream.of(
                Arguments.of(null,legacy,admin),Arguments.of(List.of(),legacy,admin))));
    }

    @ParameterizedTest
    @MethodSource("invalidPolicies")
    void rejectsMissingPolicyBeforeBuildingCriteria(Collection<DocumentStatus> statuses, boolean legacyGlobal, boolean adminReadAll) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> DocumentRepository.readableDrafts(10L, legacyGlobal, adminReadAll, statuses));

        assertEquals("guardedStatuses must not be null or empty", error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidPolicies")
    void searchRejectsMissingPolicyBeforeExecutingAnyQuery(Collection<DocumentStatus> statuses, boolean legacyGlobal, boolean adminReadAll) {
        var repository = mock(DocumentRepository.class, CALLS_REAL_METHODS);

        var error = assertThrows(IllegalArgumentException.class,
                () -> repository.searchReadableDrafts(10L, legacyGlobal, adminReadAll, statuses,
                        DocumentStatus.DRAFT, null, null, PageRequest.of(0, 10)));

        assertEquals("guardedStatuses must not be null or empty", error.getMessage());
        verify(repository, never()).findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }
}
