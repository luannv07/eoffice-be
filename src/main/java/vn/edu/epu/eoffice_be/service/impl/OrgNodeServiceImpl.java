package vn.edu.epu.eoffice_be.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.OrgNodeResponse;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.service.OrgNodeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrgNodeServiceImpl implements OrgNodeService {

	private static final String ORG_NODE_TYPE_CD = "ORG_NODE_TYPE_CD";

	OrgNodeRepository orgNodeRepository;
	DictionaryItemRepository dictionaryItemRepository;

	@Override
	@Transactional(readOnly = true)
	public List<OrgNodeResponse> getAll(OrgNodeFilterRequest request) {
		List<OrgNodeResponse> allNodes = orgNodeRepository.findAllByDeleteFlgFalseOrderByOrgOrderAsc()
						.stream()
						.filter(node -> matchesFilter(node, request))
						.map(this::convertToDto)
						.toList();
		return buildTree(allNodes);
	}

	@Override
	@Transactional
	public List<OrgNodeResponse> update(OrgNodeUpdateRequest request) {
		OrgNode node = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getId())
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

		Long newParentId = request.getParentId();
		boolean parentChanged = (newParentId == null)
						? node.getParent() != null
						: (node.getParent() == null || !newParentId.equals(node.getParent().getId()));

		if (parentChanged) {
			OrgNode newParent = null;
			if (newParentId != null) {
				newParent = orgNodeRepository.findByIdAndDeleteFlgFalse(newParentId)
								.orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_PARENT_NOT_FOUND));
				if (newParentId.equals(node.getId()) || isAncestorOf(node.getId(), newParentId)) {
					throw new BusinessException(ErrorCode.ORG_NODE_CYCLE);
				}
			}
			node.setParent(newParent);
		}

		node.setNameVn(request.getNameVn());
		node.setNameEn(request.getNameEn());
		orgNodeRepository.save(node);

		return buildTree(orgNodeRepository.findAllByDeleteFlgFalseOrderByOrgOrderAsc()
						.stream()
						.map(this::convertToDto)
						.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public OrgNodeResponse getDetail(Long id) {
		return orgNodeRepository.findByIdAndDeleteFlgFalse(id)
						.map(this::convertToDto)
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	@Override
	@Transactional
	public void delete(Long id) {
		OrgNode node = orgNodeRepository.findByIdAndDeleteFlgFalse(id)
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (!orgNodeRepository.findAllByParentIdAndDeleteFlgFalse(id).isEmpty()) {
			throw new BusinessException(ErrorCode.ORG_NODE_HAS_CHILDREN);
		}
		node.setDeleteFlg(true);
		orgNodeRepository.save(node);
	}

	@Override
	@Transactional
	public void create(OrgNodeCreateRequest request) {
		if (orgNodeRepository.findByCodeAndDeleteFlgFalse(request.getCode()).isPresent()) {
			throw new BusinessException(ErrorCode.ORG_NODE_CODE_EXISTS);
		}
		validateTypeCd(request.getTypeCd());
		OrgNode parent = resolveParent(request.getParentId());

		OrgNode node = OrgNode.builder()
						.code(request.getCode())
						.parent(parent)
						.nameVn(request.getNameVn())
						.nameEn(request.getNameEn())
						.typeCd(request.getTypeCd())
						.orgOrder(request.getOrgOrder() != null ? request.getOrgOrder() : 0)
						.build();
		orgNodeRepository.save(node);
	}

	/**
	 * Kiểm tra node {@code candidateNodeId} có nằm trong chuỗi tổ tiên của {@code parentId} hay không.
	 * Nếu có, gán node vào cha = {parent} sẽ tạo cycle.
	 */
	private boolean isAncestorOf(Long candidateNodeId, Long parentId) {
		List<OrgNode> ancestors = orgNodeRepository.findActiveAncestors(parentId);
		return ancestors.stream().anyMatch(ancestor -> ancestor.getId().equals(candidateNodeId));
	}

	private OrgNode resolveParent(Long parentId) {
		if (parentId == null) {
			return null;
		}
		return orgNodeRepository.findByIdAndDeleteFlgFalse(parentId)
						.orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_PARENT_NOT_FOUND));
	}

	private void validateTypeCd(String typeCd) {
		if (typeCd == null || typeCd.isBlank()) {
			throw new BusinessException(ErrorCode.ORG_NODE_TYPE_INVALID);
		}
		dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse(ORG_NODE_TYPE_CD, typeCd)
						.orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_TYPE_INVALID));
	}

	/**
	 * Xây cây org trả về danh sách node gốc (root). Tái sử dụng cho {@link #getAll} và {@link #update}.
	 */
	private List<OrgNodeResponse> buildTree(List<OrgNodeResponse> allNodes) {
		Map<Long, OrgNodeResponse> nodeMap = allNodes.stream()
						.collect(Collectors.toMap(OrgNodeResponse::getId, Function.identity()));
		List<OrgNodeResponse> rootNodes = new ArrayList<>();

		for (OrgNodeResponse node : allNodes) {
			if (node.getParentId() != null) {
				OrgNodeResponse parent = nodeMap.get(node.getParentId());
				if (parent != null) {
					if (parent.getChildren() == null) {
						parent.setChildren(new ArrayList<>());
					}
					parent.getChildren().add(node);
				}
			} else {
				rootNodes.add(node);
			}
		}
		return rootNodes;
	}

	private boolean matchesFilter(OrgNode node, OrgNodeFilterRequest request) {
		if (request == null) {
			return true;
		}
		if (request.getCode() != null && !request.getCode().isBlank()
						&& !node.getCode().toLowerCase().contains(request.getCode().toLowerCase())) {
			return false;
		}
		if (request.getNameVn() != null && !request.getNameVn().isBlank()
						&& !node.getNameVn().toLowerCase().contains(request.getNameVn().toLowerCase())) {
			return false;
		}
		return request.getTypeCd() == null || request.getTypeCd().isBlank()
						|| request.getTypeCd().equals(node.getTypeCd());
	}

	private OrgNodeResponse convertToDto(OrgNode entity) {
		return OrgNodeResponse.builder()
						.id(entity.getId())
						.parentId(entity.getParent() != null ? entity.getParent().getId() : null)
						.code(entity.getCode())
						.nameVn(entity.getNameVn())
						.nameEn(entity.getNameEn())
						.typeCd(entity.getTypeCd())
						.orgOrder(entity.getOrgOrder())
						.children(new ArrayList<>())
						.build();
	}
}