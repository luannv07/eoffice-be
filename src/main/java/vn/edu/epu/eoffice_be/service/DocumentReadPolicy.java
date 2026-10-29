package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import java.util.Set;

/** Draft access only. Registry and published documents retain their existing policies. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentReadPolicy {
    public static final Set<DocumentStatus> STATUSES = Set.of(DocumentStatus.DRAFT, DocumentStatus.IN_REVIEW,
            DocumentStatus.PENDING_APPROVAL, DocumentStatus.NEEDS_REVISION, DocumentStatus.REJECTED);
    private final UserLoginRepository users;
    private final DocumentApprovalRequestRepository requests;

    public boolean applies(Document document) {
        return document != null && document.getStatus() != null && STATUSES.contains(document.getStatus());
    }

    public UserLogin requireUser(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        UserLogin user = users.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.isDeleteFlg() || !"ACTIVE".equals(user.getUserStatusCd())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    public UserLogin currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (authentication.getPrincipal() instanceof JwtUserPrincipal principal) return requireUser(principal.userId());
        var user = users.findByUsernameAndDeleteFlgFalse(authentication.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return requireUser(user.getId());
    }

    public void assertRead(Document document, Long userId) {
        var user = requireUser(userId);
        if (document == null || document.isDeleteFlg()) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        if (!applies(document)) throw new IllegalArgumentException("Use the legacy policy for this document status");
        if (document.getDrafter() != null && user.getId().equals(document.getDrafter().getId())) return;
        if (document.getId() != null && requests.existsParticipant(document.getId(), user.getId())) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem văn bản này.");
    }

    /** One account lookup and one history query for a complete list. */
    public Set<Long> readableIds(java.util.Collection<Document> documents) {
        var scoped = documents.stream().filter(this::applies).filter(d -> !d.isDeleteFlg()).toList();
        if (scoped.isEmpty()) return Set.of();
        var user = currentUser();
        var ids = scoped.stream().map(Document::getId).filter(java.util.Objects::nonNull).toList();
        Set<Long> readable = new java.util.HashSet<>(ids.isEmpty() ? java.util.List.of()
                : requests.findParticipantDocumentIds(ids, user.getId()));
        scoped.stream().filter(d -> d.getDrafter() != null && user.getId().equals(d.getDrafter().getId()))
                .map(Document::getId).filter(java.util.Objects::nonNull).forEach(readable::add);
        return readable;
    }

    /** Read permission must never confer restore/edit rights to a historical approver. */
    public void assertDraftWrite(Document document, Long userId) {
        var user = requireUser(userId);
        if (document.getDrafter() == null || !user.getId().equals(document.getDrafter().getId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền sửa dự thảo này.");
    }
}
