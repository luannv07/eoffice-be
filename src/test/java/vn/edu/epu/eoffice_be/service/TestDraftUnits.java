package vn.edu.epu.eoffice_be.service;

import java.util.*;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.epu.eoffice_be.config.AdminSimulationProperties;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Legacy routing tests opt into simulation explicitly and supply their existing unit fixtures. */
public final class TestDraftUnits {
    private TestDraftUnits() { }
    public static DraftUnitResolver resolver(PositionAssignmentRepository positions) {
        var config = new AdminSimulationProperties(new MockEnvironment().withProperty("spring.profiles.active", "test"));
        config.setAdminSimulationEnabled(true);
        var orgs = mock(OrgNodeRepository.class);
        lenient().when(orgs.findByIdAndDeleteFlgFalse(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.of(OrgNode.builder().id(id)
                    .typeCd(id == 200L ? "DEPARTMENT" : id == 1L ? "UNIVERSITY" : "FACULTY").build());
        });
        return new DraftUnitResolver(positions, orgs, config);
    }
}
