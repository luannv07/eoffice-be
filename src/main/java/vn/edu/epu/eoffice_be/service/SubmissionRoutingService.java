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
        if (isAdmin(sender)) {
            List<PositionAssignment> candidates = candidates(RECEIVERS, null, at);
            validateLeadershipCounts(candidates, false);
            List<RouteOption> routes = new ArrayList<>();
            addRoute(routes, SubmissionRoute.REVIEW, candidates.stream().filter(p -> REVIEWERS.contains(p.getPositionCd())).toList());
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
        List<PositionAssignment> candidates = candidates(route.positions(), route.unitId(), at);
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
        if (isAdmin(sender) && (request.getRoute() == null || request.getApproverUserId() == null)) {
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
            throw new BusinessException(ErrorCode.APPROVER_NOT_ELIGIBLE,
                    "Người được chọn không thuộc tuyến hoặc đơn vị được phép nhận văn bản");
        }
        return new Decision(selected, id);
    }

    private SenderRoute senderRoute(UserLogin sender, Instant at) {
        List<PositionAssignment> assignments = positions.findActiveAssignments(sender.getId(), at).stream()
                .filter(p -> validAssignment(p, at)).toList();
        if (assignments.stream().anyMatch(p -> UNSUPPORTED.contains(p.getPositionCd()))) {
            throw bad("Chức danh chưa có tuyến gửi duyệt được quy định trên hệ thống");
        }
        if (sender.getRole() == Role.CLERK
                && assignments.stream().anyMatch(p -> !"VT".equals(p.getPositionCd()))) {
            throw bad("Không xác định được duy nhất tư cách/đơn vị gửi. Vui lòng kiểm tra các phân nhiệm đang hiệu lực.");
        }
        Set<SenderRoute> routes = new HashSet<>();
        for (PositionAssignment p : assignments) {
            String cd = p.getPositionCd();
            if ("HT".equals(cd)) routes.add(new SenderRoute(SubmissionRoute.CLERK_QUEUE, null, Set.of(), false));
            else if ("PHT".equals(cd)) routes.add(new SenderRoute(SubmissionRoute.LEADER_APPROVAL, null, Set.of("HT"), false));
            else if (REVIEWERS.contains(cd) || "VT".equals(cd)) {
                routes.add(new SenderRoute(SubmissionRoute.LEADER_APPROVAL, null, LEADERS, false));
            } else if (sender.getRole() == Role.USER) {
                String type = p.getOrgNode().getTypeCd();
                if ("FACULTY".equals(type)) routes.add(new SenderRoute(SubmissionRoute.REVIEW,
                        p.getOrgNode().getId(), Set.of("TK", "PK"), true));
                else if ("DEPARTMENT".equals(type)) routes.add(new SenderRoute(SubmissionRoute.REVIEW,
                        p.getOrgNode().getId(), Set.of("TP", "PP"), true));
                else throw bad("Đơn vị của USER không phải khoa/phòng hợp lệ");
            } else throw bad("Tài khoản chưa có chức vụ/phân nhiệm hợp lệ để xác định tuyến gửi.");
        }
        if (sender.getRole() == Role.CLERK) routes.add(new SenderRoute(SubmissionRoute.LEADER_APPROVAL, null, LEADERS, false));
        // Different units/positions are ambiguous even when their destinations happen to match.
        long identities = assignments.stream().map(p -> p.getPositionCd() + ":" + p.getOrgNode().getId()).distinct().count();
        if (identities > 1 || routes.size() > 1) {
            throw bad("Không xác định được duy nhất tư cách/đơn vị gửi. Vui lòng kiểm tra các phân nhiệm đang hiệu lực.");
        }
        if (routes.isEmpty()) throw bad(sender.getRole() == Role.USER
                ? "Tài khoản USER chưa được phân vào khoa/phòng."
                : "Tài khoản chưa có chức vụ/phân nhiệm hợp lệ để xác định tuyến gửi.");
        return routes.iterator().next();
    }

    private List<PositionAssignment> candidates(Set<String> cds, Long unit, Instant at) {
        return positions.findSubmissionCandidates(cds, unit, at).stream()
                .filter(p -> validAssignment(p, at) && activeUser(p.getUserLogin()))
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
