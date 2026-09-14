package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.OrgNodeResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.service.impl.OrgNodeServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgNodeServiceImplTest {

	@Mock
	OrgNodeRepository orgNodeRepository;

	@Mock
	DictionaryItemRepository dictionaryItemRepository;

	@InjectMocks
	OrgNodeServiceImpl orgNodeService;

	private OrgNode node(Long id, OrgNode parent) {
		return OrgNode.builder()
						.id(id)
						.parent(parent)
						.code("C" + id)
						.nameVn("Tên " + id)
						.nameEn("Name " + id)
						.typeCd("FACULTY")
						.orgOrder(0)
						.build();
	}

	// ---- getRoots / getChildren (new endpoints) ----

	@Test
	void getRootsShouldReturnRootNodesWithNullParent() {
		OrgNode a = node(1L, null);
		OrgNode b = node(2L, null);
		when(orgNodeRepository.findByParentIdIsNullAndDeleteFlgFalseOrderByOrgOrderAsc())
						.thenReturn(List.of(a, b));

		List<OrgNodeResponse> result = orgNodeService.getRoots();

		assertEquals(2, result.size());
		assertNull(result.get(0).getParentId());
		assertNull(result.get(1).getParentId());
	}

	@Test
	void getChildrenShouldMapParentId() {
		OrgNode child = node(51L, node(5L, null));
		when(orgNodeRepository.findAllByParentIdAndDeleteFlgFalseOrderByOrgOrderAsc(5L))
						.thenReturn(List.of(child));

		List<OrgNodeResponse> result = orgNodeService.getChildren(5L);

		assertEquals(1, result.size());
		assertEquals(5L, result.get(0).getParentId());
	}

	@Test
	void getDetailShouldReturnDtoWhenFound() {
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(7L)).thenReturn(Optional.of(node(7L, null)));

		OrgNodeResponse result = orgNodeService.getDetail(7L);

		assertEquals(7L, result.getId());
		assertEquals("C7", result.getCode());
	}

	// ---- update (orgOrder + re-parent) ----

	@Test
	void updateShouldSetOrgOrderWhenProvided() {
		OrgNode existing = node(10L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findAllByDeleteFlgFalseOrderByOrgOrderAsc()).thenReturn(List.of());

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(10L);
		request.setParentId(null);
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setOrgOrder(3);

		orgNodeService.update(request);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertEquals(3, captor.getValue().getOrgOrder());
	}

	@Test
	void updateShouldKeepOrgOrderWhenNull() {
		OrgNode existing = node(10L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findAllByDeleteFlgFalseOrderByOrgOrderAsc()).thenReturn(List.of());

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(10L);
		request.setParentId(null);
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setOrgOrder(null);

		orgNodeService.update(request);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertEquals(0, captor.getValue().getOrgOrder());
	}

	@Test
	void updateShouldReparentWhenParentChanged() {
		OrgNode existing = node(10L, null);
		OrgNode newParent = node(5L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(5L)).thenReturn(Optional.of(newParent));
		when(orgNodeRepository.findActiveAncestors(5L)).thenReturn(List.of());
		when(orgNodeRepository.findAllByDeleteFlgFalseOrderByOrgOrderAsc()).thenReturn(List.of());

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(10L);
		request.setParentId(5L);
		request.setNameVn("Mới");
		request.setNameEn("New");

		orgNodeService.update(request);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertNotNull(captor.getValue().getParent());
		assertEquals(5L, captor.getValue().getParent().getId());
	}

	@Test
	void updateShouldThrowCycleWhenNewParentIsSelf() {
		OrgNode existing = node(10L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(10L);
		request.setParentId(10L);
		request.setNameVn("Mới");
		request.setNameEn("New");

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.update(request));
		assertEquals(ErrorCode.ORG_NODE_CYCLE, ex.getErrorCode());
		verify(orgNodeRepository, never()).save(any());
	}

	@Test
	void updateShouldThrowParentNotFound() {
		OrgNode existing = node(10L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(99L)).thenReturn(Optional.empty());

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(10L);
		request.setParentId(99L);
		request.setNameVn("Mới");
		request.setNameEn("New");

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.update(request));
		assertEquals(ErrorCode.ORG_NODE_PARENT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void updateShouldThrowNotFound() {
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(404L)).thenReturn(Optional.empty());

		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setId(404L);
		request.setParentId(null);
		request.setNameVn("Mới");
		request.setNameEn("New");

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.update(request));
		assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
	}

	// ---- create (orgOrder default + validations) ----

	@Test
	void createShouldDefaultOrgOrderToZeroWhenNull() {
		when(orgNodeRepository.findByCodeAndDeleteFlgFalse("NEW")).thenReturn(Optional.empty());
		when(dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse(anyString(), anyString()))
						.thenReturn(Optional.of(DictionaryItem.builder().groupCd("ORG_NODE_TYPE_CD").itemCd("FACULTY").build()));
		when(orgNodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("NEW");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("FACULTY");
		request.setOrgOrder(null);

		orgNodeService.create(request);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertEquals(0, captor.getValue().getOrgOrder());
		assertNull(captor.getValue().getParent());
	}

	@Test
	void createShouldUseProvidedOrgOrder() {
		when(orgNodeRepository.findByCodeAndDeleteFlgFalse("NEW")).thenReturn(Optional.empty());
		when(dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse(anyString(), anyString()))
						.thenReturn(Optional.of(DictionaryItem.builder().groupCd("ORG_NODE_TYPE_CD").itemCd("FACULTY").build()));
		when(orgNodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("NEW");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("FACULTY");
		request.setOrgOrder(5);

		orgNodeService.create(request);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertEquals(5, captor.getValue().getOrgOrder());
	}

	@Test
	void createShouldThrowCodeExists() {
		when(orgNodeRepository.findByCodeAndDeleteFlgFalse("DUP")).thenReturn(Optional.of(node(1L, null)));

		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("DUP");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("FACULTY");

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.create(request));
		assertEquals(ErrorCode.ORG_NODE_CODE_EXISTS, ex.getErrorCode());
		verify(orgNodeRepository, never()).save(any());
	}

	@Test
	void createShouldThrowTypeInvalid() {
		when(orgNodeRepository.findByCodeAndDeleteFlgFalse("NEW")).thenReturn(Optional.empty());
		when(dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse(anyString(), anyString()))
						.thenReturn(Optional.empty());

		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("NEW");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("BAD_TYPE");

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.create(request));
		assertEquals(ErrorCode.ORG_NODE_TYPE_INVALID, ex.getErrorCode());
	}

	// ---- delete ----

	@Test
	void deleteShouldSoftDeleteLeafNode() {
		OrgNode existing = node(11L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(11L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findAllByParentIdAndDeleteFlgFalse(11L)).thenReturn(List.of());

		orgNodeService.delete(11L);

		ArgumentCaptor<OrgNode> captor = ArgumentCaptor.forClass(OrgNode.class);
		verify(orgNodeRepository).save(captor.capture());
		assertTrue(captor.getValue().isDeleteFlg());
	}

	@Test
	void deleteShouldThrowHasChildren() {
		OrgNode existing = node(11L, null);
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(11L)).thenReturn(Optional.of(existing));
		when(orgNodeRepository.findAllByParentIdAndDeleteFlgFalse(11L))
						.thenReturn(List.of(node(12L, null)));

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.delete(11L));
		assertEquals(ErrorCode.ORG_NODE_HAS_CHILDREN, ex.getErrorCode());
		verify(orgNodeRepository, never()).save(any());
	}

	@Test
	void deleteShouldThrowNotFound() {
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(404L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> orgNodeService.delete(404L));
		assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
	}
}
