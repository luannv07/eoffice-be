package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionAssignmentSubmissionGuardTest {
    private static Stream<Collection<String>> absentCodes() {
        return Stream.of(null, List.of(), Set.of());
    }

    @ParameterizedTest
    @MethodSource("absentCodes")
    void absentCodesReturnEmptyWithoutExecutingEitherQuery(Collection<String> codes) {
        var repository = mock(PositionAssignmentRepository.class, CALLS_REAL_METHODS);
        Instant at = Instant.parse("2026-10-29T00:00:00Z");

        assertTrue(repository.findSubmissionCandidates(codes, null, at).isEmpty());
        assertTrue(repository.findSubmissionCandidates(codes, 10L, at).isEmpty());
        assertTrue(repository.findSubmissionCandidatesAcrossUnits(codes, at).isEmpty());
        assertTrue(repository.findSubmissionCandidatesInUnit(codes, 10L, at).isEmpty());

        verify(repository, never()).findSubmissionCandidatesAcrossUnitsInternal(any(), any());
        verify(repository, never()).findSubmissionCandidatesInUnitInternal(any(), any(), any());
    }

    private static Stream<Long> units() { return Stream.of(null, 10L); }

    @ParameterizedTest
    @MethodSource("units")
    void validCodesKeepQueryScopeParametersAndResults(Long unit) {
        var repository = mock(PositionAssignmentRepository.class, CALLS_REAL_METHODS);
        Set<String> codes = Set.of("HT", "PHT");
        Instant at = Instant.parse("2026-10-29T00:00:00Z");
        List<PositionAssignment> result = List.of(new PositionAssignment());
        if (unit == null) {
            when(repository.findSubmissionCandidatesAcrossUnitsInternal(codes, at)).thenReturn(result);
        } else {
            when(repository.findSubmissionCandidatesInUnitInternal(codes, unit, at)).thenReturn(result);
        }

        assertSame(result, repository.findSubmissionCandidates(codes, unit, at));

        if (unit == null) {
            verify(repository).findSubmissionCandidatesAcrossUnitsInternal(codes, at);
            verify(repository, never()).findSubmissionCandidatesInUnitInternal(any(), any(), any());
        } else {
            verify(repository).findSubmissionCandidatesInUnitInternal(codes, unit, at);
            verify(repository, never()).findSubmissionCandidatesAcrossUnitsInternal(any(), any());
        }
    }
}
