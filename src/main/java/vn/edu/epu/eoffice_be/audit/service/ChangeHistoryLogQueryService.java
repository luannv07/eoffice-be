package vn.edu.epu.eoffice_be.audit.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.epu.eoffice_be.audit.dto.request.ChangeHistoryLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.ChangeHistoryLogResponse;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import vn.edu.epu.eoffice_be.audit.repository.ChangeHistoryLogRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChangeHistoryLogQueryService {

	private final ChangeHistoryLogRepository changeHistoryLogRepository;

	@Transactional(readOnly = true)
	public Page<ChangeHistoryLogResponse> filter(ChangeHistoryLogFilterRequest request) {
		String tableName = trimToNull(request.getTableName());
		String objectId = trimToNull(request.getObjectId());
		String changedBy = trimToNull(request.getChangedBy());
		String action = trimToNull(request.getAction());

		Specification<ChangeHistoryLog> specification = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (tableName != null) {
				predicates.add(cb.equal(root.get("tableName"), tableName));
			}

			if (objectId != null) {
				predicates.add(cb.equal(root.get("objectId"), objectId));
			}

			if (changedBy != null) {
				predicates.add(cb.equal(root.get("changedBy"), changedBy));
			}

			if (action != null && shouldApplyActionFilter(tableName, objectId, changedBy, request)) {
				predicates.add(cb.equal(root.get("action"), action));
			}

			if (request.getChangedFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), request.getChangedFrom()));
			}

			if (request.getChangedTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), request.getChangedTo()));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};

		return changeHistoryLogRepository.findAll(specification, request.pageable())
						.map(this::toResponse);
	}

	boolean shouldApplyActionFilter(
					String tableName,
					String objectId,
					String changedBy,
					ChangeHistoryLogFilterRequest request
	) {
		boolean hasObjectLeadingIndex = tableName != null && objectId != null;
		boolean hasChangedByLeadingIndex = changedBy != null
						&& (request.getChangedFrom() != null || request.getChangedTo() != null);
		return hasObjectLeadingIndex || hasChangedByLeadingIndex;
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private ChangeHistoryLogResponse toResponse(ChangeHistoryLog entity) {
		return ChangeHistoryLogResponse.builder()
						.id(entity.getId())
						.tableName(entity.getTableName())
						.objectId(entity.getObjectId())
						.action(entity.getAction())
						.changedBy(entity.getChangedBy())
						.changedAt(entity.getChangedAt())
						.sourceService(entity.getSourceService())
						.beforeData(entity.getBeforeData())
						.afterData(entity.getAfterData())
						.changedFields(entity.getChangedFields())
						.build();
	}
}
