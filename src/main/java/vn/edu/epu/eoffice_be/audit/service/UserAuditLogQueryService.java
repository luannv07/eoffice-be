package vn.edu.epu.eoffice_be.audit.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.epu.eoffice_be.audit.dto.request.UserAuditLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.UserAuditLogResponse;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.repository.UserAuditLogRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuditLogQueryService {

    private final UserAuditLogRepository userAuditLogRepository;

    @Transactional(readOnly = true)
    public Page<UserAuditLogResponse> filter(UserAuditLogFilterRequest request) {
        Specification<UserAuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getUsername())) {
                predicates.add(cb.equal(root.get("username"), request.getUsername().trim()));
            }

            if (StringUtils.hasText(request.getRequestUri())) {
                predicates.add(cb.equal(root.get("requestUri"), request.getRequestUri().trim()));
            }

            if (request.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), request.getCreatedFrom()));
            }

            if (request.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), request.getCreatedTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return userAuditLogRepository.findAll(specification, request.pageable())
                .map(this::toResponse);
    }

    private UserAuditLogResponse toResponse(UserAuditLog entity) {
        return UserAuditLogResponse.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .userAgent(entity.getUserAgent())
                .clientIp(entity.getClientIp())
                .requestUri(entity.getRequestUri())
                .methodName(entity.getMethodName())
                .executionTimeMs(entity.getExecutionTimeMs())
                .paramContent(entity.getParamContent())
                .createdDate(entity.getCreatedDate())
                .build();
    }
}
