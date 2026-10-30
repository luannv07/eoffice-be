package vn.edu.epu.eoffice_be.dto.response;
import java.util.List;
public record DraftOptionsResponse(List<IssuingOrg> issuingOrgs, boolean issuingOrgRequired,
                                   boolean adminSimulationEnabled) {
    public record IssuingOrg(Long id, String code, String name, String typeCd) { }
}
