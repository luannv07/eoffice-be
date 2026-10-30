package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.EligibleApproverResponse;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse.RouteOption;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse.SelectionMode;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** First-submission policy. No storage, signer or workflow mutations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionRoutingService {
    private static final Set<String> REVIEWERS = Set.of("TK", "PK", "TP", "PP");
    private static final Set<String> LEADERS = Set.of("HT", "PHT");
    private static final Set<String> RECEIVERS = Set.of("HT", "PHT", "TK", "PK", "TP", "PP");
    private static final Set<String> UNSUPPORTED = Set.of("GĐTT", "GDTT", "PGDTT", "BT", "PBT", "UV");
    private final PositionAssignmentRepository positions;
    private final UserInfoRepository profiles;
    private final DraftUnitResolver units;

    public record Decision(SubmissionRoute route, Long approverUserId) {}
    private record SenderRoute(SubmissionRoute route, Long unitId, Set<String> positions, boolean requiresChoice) {}

    public static void assertOwnership(Document document, UserLogin sender) {
        if (sender == null || sender.getId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (document == null) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        if (document.getDrafter() == null || document.getDrafter().getId() == null
                || !document.getDrafter().getId().equals(sender.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền gửi duyệt văn bản này");
        }
    }

    public SubmissionOptionsResponse options(Document document, UserLogin sender, Instant at) {
        assertOwnership(document, sender);
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Thông tin tuyến gửi lần đầu chỉ áp dụng cho dự thảo DRAFT");
        }
        if (!activeUser(sender)) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        OrgNode selectedUnit = units.resolve(sender, document.getOrgId(), at);
        if (units.simulated(sender)) {
            List<PositionAssignment> candidates = candidates(RECEIVERS, null, at).stream()
                    .filter(p -> !sender.getId().equals(p.getUserLogin().getId())).toList();
            validateLeadershipCounts(candidates, false);
            List<RouteOption> routes = new ArrayList<>();
            if (selectedUnit != null) {
                Set<String> reviewers = "FACULTY".equals(selectedUnit.getTypeCd()) ? Set.of("TK", "PK")
                        : "DEPARTMENT".equals(selectedUnit.getTypeCd()) ? Set.of("TP", "PP") : Set.of();
                addRoute(routes, SubmissionRoute.REVIEW, candidates.stream()
                        .filter(p -> reviewers.contains(p.getPositionCd()) && selectedUnit.getId().equals(p.getOrgNode().getId())).toList());
            }
            addRoute(routes, SubmissionRoute.LEADER_APPROVAL, candidates.stream().filter(p -> LEADERS.contains(p.getPositionCd())).toList());
            if (routes.isEmpty()) throw bad("Không có người nhận có chức vụ hợp lệ để kiểm thử");
            return new SubmissionOptionsResponse(SelectionMode.SELECT, List.copyOf(routes));
        }
        SenderRoute route = senderRoute(sender, at);
        if (route.route() == SubmissionRoute.CLERK_QUEUE) {
            List<PositionAssignment> leaders = candidates(LEADERS, null, at);
            validateLeadershipCounts(leaders, true);
            boolean isOnlyRector = leaders.stream().anyMatch(p -> "HT".equals(p.getPositionCd())
                    && sender.getId().equals(p.getUserLogin().getId()));
            if (!isOnlyRector) throw bad("Không xác định được tài khoản Hiệu trưởng hợp lệ");
            return new SubmissionOptionsResponse(SelectionMode.QUEUE,
                    List.of(new RouteOption(route.route(), route.route().getNextStatus(), List.of())));
        }
        List<PositionAssignment> candidates = candidates(route.positions(), route.unitId(), at).stream()
                .filter(p -> !sender.getId().equals(p.getUserLogin().getId())).toList();
        if (route.route() == SubmissionRoute.LEADER_APPROVAL) {
            // Validate the global HT/PHT invariant even when a PHT may only choose HT.
            List<PositionAssignment> leaders = route.positions().equals(LEADERS)
                    ? candidates : candidates(LEADERS, null, at);
            validateLeadershipCounts(leaders, true);
        }
        if (candidates.isEmpty()) throw bad(route.route() == SubmissionRoute.REVIEW
                ? "Không có trưởng/phó khoa hoặc phòng hợp lệ tại đơn vị của tài khoản"
                : "Không có Hiệu trưởng/Phó Hiệu trưởng hợp lệ để nhận văn bản");
        List<RouteOption> options = new ArrayList<>();
        addRoute(options, route.route(), candidates);
        SelectionMode mode = route.requiresChoice() || options.getFirst().approvers().size() > 1
                ? SelectionMode.SELECT : SelectionMode.AUTO;
        return new SubmissionOptionsResponse(mode, List.copyOf(options));
    }

    public Decision resolve(Document document, UserLogin sender, SubmitApprovalRequest request, Instant at) {
        SubmissionOptionsResponse options = options(document, sender, at);
        if (request == null) throw bad("Thiếu thông tin gửi văn bản");
        if (units.simulated(sender) && (request.getRoute() == null || request.getApproverUserId() == null)) {
            throw bad("ADMIN/SYSTEM_ADMIN phải chọn tuyến và người nhận để kiểm thử");
        }
        SubmissionRoute selected = request.getRoute();
        if (selected == null) selected = options.routes().getFirst().route();
        SubmissionRoute selectedRoute = selected;
        RouteOption route = options.routes().stream().filter(r -> r.route() == selectedRoute).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_ELIGIBLE,
                        "Tài khoản không được phép sử dụng tuyến gửi đã chọn"));
        if (selected == SubmissionRoute.CLERK_QUEUE) {
            if (request.getApproverUserId() != null) throw bad("Hàng chờ văn thư không nhận ID văn thư cá nhân");
            return new Decision(selected, null);
        }
        Long id = request.getApproverUserId();
        if (id == null && options.selectionMode() == SelectionMode.AUTO) id = route.approvers().getFirst().getUserId();
        if (id == null) throw bad("Vui lòng chọn người nhận hợp lệ");
        Long approverId = id;
        if (route.approvers().stream().noneMatch(p -> approverId.equals(p.getUserId()))) {
            if (selectedRoute == SubmissionRoute.REVIEW) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Người nhận được chọn không thuộc phạm vi thẩm định của khoa/phòng bạn.");
            }
            throw new BusinessException(ErrorCode.APPROVER_NOT_ELIGIBLE,
                    "Người được chọn không thuộc tuyến hoặc đơn vị được phép nhận văn bản");
        }
        return new Decision(selected, id);
    }

    private SenderRoute senderRoute(UserLogin sender, Instant at) {
        var identity = units.identity(sender, at);
        return new SenderRoute(identity.route(), identity.route() == SubmissionRoute.REVIEW ? identity.unit().getId() : null,
                identity.recipients(), identity.choice());
    }

    private List<PositionAssignment> candidates(Set<String> cds, Long unit, Instant at) {
        return positions.findSubmissionCandidates(cds, unit, at).stream()
                .filter(p -> validAssignment(p, at) && activeUser(p.getUserLogin()) && !DocumentAdminPolicy.isAdmin(p.getUserLogin()))
                .filter(p -> cds.contains(p.getPositionCd()))
                .filter(p -> unit == null || unit.equals(p.getOrgNode().getId())).toList();
    }

    private boolean validAssignment(PositionAssignment p, Instant at) {
        return p != null && !p.isDeleteFlg() && p.getActualDismissalDate() == null
                && p.getEffectiveFrom() != null && !p.getEffectiveFrom().isAfter(at)
                && (p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(at))
                && p.getPositionCd() != null && p.getOrgNode() != null && p.getOrgNode().getId() != null
                && !p.getOrgNode().isDeleteFlg() && p.getUserLogin() != null && p.getUserLogin().getId() != null;
    }

    private void validateLeadershipCounts(List<PositionAssignment> candidates, boolean requireRector) {
        for (String cd : List.of("HT", "PHT")) {
            long count = candidates.stream().filter(p -> cd.equals(p.getPositionCd()))
                    .map(p -> p.getUserLogin().getId()).distinct().count();
            if (count > 1) throw bad("Có nhiều tài khoản " + cd + " hợp lệ. Vui lòng kiểm tra phân nhiệm.");
            if (requireRector && "HT".equals(cd) && count == 0) throw bad("Chưa có Hiệu trưởng hợp lệ để nhận văn bản");
        }
    }

    private void addRoute(List<RouteOption> routes, SubmissionRoute route, List<PositionAssignment> candidates) {
        if (candidates.isEmpty()) return;
        Comparator<PositionAssignment> order = Comparator.comparing(PositionAssignment::getPositionCd)
                .thenComparing(p -> p.getOrgNode().getId())
                .thenComparing(PositionAssignment::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        Map<Long, PositionAssignment> unique = new LinkedHashMap<>();
        candidates.stream().sorted(order).forEach(p -> unique.putIfAbsent(p.getUserLogin().getId(), p));
        Map<Long, UserInfoRepository.DrafterName> names = profiles.findDrafterNamesByUserIds(unique.keySet()).stream()
                .collect(Collectors.toMap(UserInfoRepository.DrafterName::getUserId, p -> p));
        List<EligibleApproverResponse> approvers = unique.values().stream().map(p -> {
            UserLogin u = p.getUserLogin();
            var name = names.get(u.getId());
            String username = name == null ? u.getUsername() : name.getUsername();
            return EligibleApproverResponse.builder().userId(u.getId()).username(username)
                    .fullName(name != null && name.getFullName() != null ? name.getFullName() : username)
                    .email(u.getEmail()).positionCd(p.getPositionCd()).positionName(positionName(p.getPositionCd()))
                    .orgNodeId(p.getOrgNode().getId()).orgNodeName(p.getOrgNode().getNameVn()).build();
        }).sorted(Comparator.comparing(EligibleApproverResponse::getUsername, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EligibleApproverResponse::getUserId)).toList();
        routes.add(new RouteOption(route, route.getNextStatus(), approvers));
    }

    public static boolean isAdmin(UserLogin user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
    }
    private boolean activeUser(UserLogin user) {
        return user != null && !user.isDeleteFlg() && "ACTIVE".equals(user.getUserStatusCd());
    }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private String positionName(String cd) {
        return switch (cd) {
            case "HT" -> "Hiệu trưởng"; case "PHT" -> "Phó Hiệu trưởng";
            case "TK" -> "Trưởng khoa"; case "PK" -> "Phó Trưởng khoa";
            case "TP" -> "Trưởng phòng"; case "PP" -> "Phó Trưởng phòng";
            default -> cd;
        };
    }
}
