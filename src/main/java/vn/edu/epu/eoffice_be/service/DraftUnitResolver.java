package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.config.AdminSimulationProperties;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DraftOptionsResponse;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftUnitResolver {
    private final PositionAssignmentRepository positions;
    private final OrgNodeRepository orgs;
    private final AdminSimulationProperties simulation;
    private static final Set<String> REVIEWERS = Set.of("TK", "PK", "TP", "PP");
    private static final Set<String> UNSUPPORTED = Set.of("GĐTT", "GDTT", "PGDTT", "BT", "PBT", "UV");
    public record Identity(SubmissionRoute route, OrgNode unit, Set<String> recipients, boolean choice) { }

    public boolean simulated(UserLogin user) { return simulation.allowed(user); }

    public Identity identity(UserLogin user, Instant at) {
        List<PositionAssignment> assignments = positions.findActiveAssignments(user.getId(), at).stream()
                .filter(p -> valid(p, at)).toList();
        if (assignments.stream().anyMatch(p -> UNSUPPORTED.contains(p.getPositionCd())))
            throw bad("Chức danh chưa có tuyến gửi duyệt được quy định trên hệ thống");
        if (user.getRole() == Role.CLERK && assignments.stream().anyMatch(p -> !"VT".equals(p.getPositionCd())))
            throw ambiguous();
        if (assignments.stream().map(p -> p.getPositionCd() + ":" + p.getOrgNode().getId()).distinct().count() > 1)
            throw ambiguous();
        if (assignments.isEmpty()) {
            if (user.getRole() == Role.CLERK) return new Identity(SubmissionRoute.LEADER_APPROVAL, null, Set.of("HT", "PHT"), false);
            throw bad(user.getRole() == Role.USER ? "Tài khoản USER chưa được phân vào khoa/phòng."
                    : "Tài khoản chưa có chức vụ/phân nhiệm hợp lệ để xác định tuyến gửi.");
        }
        var p = assignments.getFirst();
        var unit = p.getOrgNode();
        String cd = p.getPositionCd();
        if ("HT".equals(cd)) {
            if (AdminSimulationProperties.isAdmin(user))
                throw new BusinessException(ErrorCode.FORBIDDEN, "Admin không được đưa thẳng hồ sơ vào hàng chờ văn thư");
            return new Identity(SubmissionRoute.CLERK_QUEUE, unit, Set.of(), false);
        }
        if ("PHT".equals(cd)) return new Identity(SubmissionRoute.LEADER_APPROVAL, unit, Set.of("HT"), false);
        if (REVIEWERS.contains(cd) || "VT".equals(cd))
            return new Identity(SubmissionRoute.LEADER_APPROVAL, unit, Set.of("HT", "PHT"), false);
        if (user.getRole() != Role.USER && !AdminSimulationProperties.isAdmin(user))
            throw bad("Tài khoản chưa có chức vụ/phân nhiệm hợp lệ để xác định tuyến gửi.");
        if ("FACULTY".equals(unit.getTypeCd())) return new Identity(SubmissionRoute.REVIEW, unit, Set.of("TK", "PK"), true);
        if ("DEPARTMENT".equals(unit.getTypeCd())) return new Identity(SubmissionRoute.REVIEW, unit, Set.of("TP", "PP"), true);
        throw bad("Đơn vị của USER không phải khoa/phòng hợp lệ");
    }

    public OrgNode resolve(UserLogin user, Long requested, Instant at) {
        if (simulated(user)) return requested == null ? null : findUnit(requested);
        Identity identity = identity(user, at);
        OrgNode assigned = identity.unit();
        if (requested == null) {
            if (identity.route() == SubmissionRoute.REVIEW) throw bad("Vui lòng chọn khoa/phòng của bạn.");
            return assigned;
        }
        if (assigned == null || !requested.equals(assigned.getId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, identity.route() == SubmissionRoute.REVIEW
                    ? "Đơn vị được chọn không thuộc phạm vi khoa/phòng của bạn."
                    : "Đơn vị được chọn không thuộc đơn vị được phân nhiệm của bạn.");
        return findUnit(requested);
    }

    public DraftOptionsResponse options(UserLogin user, Instant at) {
        boolean simulated = simulated(user);
        Identity identity = simulated ? null : identity(user, at);
        List<OrgNode> units = simulated ? orgs.findAllByDeleteFlgFalseOrderByOrgOrderAsc()
                : identity.unit() == null ? List.of() : List.of(identity.unit());
        return new DraftOptionsResponse(units.stream().map(o -> new DraftOptionsResponse.IssuingOrg(
                o.getId(), o.getCode(), o.getNameVn(), o.getTypeCd())).toList(),
                !simulated && identity.route() == SubmissionRoute.REVIEW, simulated);
    }
    private OrgNode findUnit(Long id) {
        return orgs.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Đơn vị ban hành không tồn tại"));
    }
    private static boolean valid(PositionAssignment p, Instant at) {
        return p != null && !p.isDeleteFlg() && p.getActualDismissalDate() == null
                && p.getEffectiveFrom() != null && !p.getEffectiveFrom().isAfter(at)
                && (p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(at))
                && p.getPositionCd() != null && p.getOrgNode() != null && p.getOrgNode().getId() != null
                && !p.getOrgNode().isDeleteFlg();
    }
    private static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private static BusinessException ambiguous() { return bad("Không xác định được duy nhất tư cách/đơn vị gửi. Vui lòng kiểm tra các phân nhiệm đang hiệu lực."); }
}
