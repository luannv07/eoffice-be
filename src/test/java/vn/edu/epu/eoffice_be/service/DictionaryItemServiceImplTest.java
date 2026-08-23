package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemBatchDeleteRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemUpdateRequest;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryItemServiceImplTest {

    @Mock
    DictionaryItemRepository dictionaryItemRepository;

    @InjectMocks
    DictionaryItemServiceImpl dictionaryItemService;

    private DictionaryItem item(Long id, String groupCd, String itemCd, boolean deleteFlg) {
        return DictionaryItem.builder()
                .id(id)
                .groupCd(groupCd)
                .itemCd(itemCd)
                .nameVn("Tên cũ")
                .nameEn("Old name")
                .displayOrder(1)
                .activeFlg(true)
                .deleteFlg(deleteFlg)
                .build();
    }

    @Test
    void createShouldInsertNewItemWhenPairNotExists() {
        DictionaryItemCreateRequest request = new DictionaryItemCreateRequest();
        request.setGroupCd("TEST_GROUP");
        request.setItemCd("NEW_CD");
        request.setNameVn("Tên mới");
        request.setNameEn("New name");
        request.setDisplayOrder(5);
        request.setActiveFlg(false);

        when(dictionaryItemRepository.findByGroupCdAndItemCd("TEST_GROUP", "NEW_CD")).thenReturn(Optional.empty());
        when(dictionaryItemRepository.save(any(DictionaryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = dictionaryItemService.create(request);

        assertEquals("TEST_GROUP", response.getGroupCd());
        assertEquals("NEW_CD", response.getItemCd());
        assertEquals(5, response.getDisplayOrder());
        assertFalse(response.getActiveFlg());

        ArgumentCaptor<DictionaryItem> captor = ArgumentCaptor.forClass(DictionaryItem.class);
        verify(dictionaryItemRepository).save(captor.capture());
        DictionaryItem saved = captor.getValue();
        assertNull(saved.getId());
        assertFalse(saved.isDeleteFlg());
    }

    @Test
    void createShouldRestoreSoftDeletedRowWhenPairExistsAsDeleted() {
        DictionaryItemCreateRequest request = new DictionaryItemCreateRequest();
        request.setGroupCd("TEST_GROUP");
        request.setItemCd("DEL_CD");
        request.setNameVn("Tên mới");
        request.setNameEn("New name");
        request.setDisplayOrder(7);
        request.setActiveFlg(true);

        DictionaryItem softDeleted = item(99L, "TEST_GROUP", "DEL_CD", true);

        when(dictionaryItemRepository.findByGroupCdAndItemCd("TEST_GROUP", "DEL_CD")).thenReturn(Optional.of(softDeleted));
        when(dictionaryItemRepository.save(any(DictionaryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = dictionaryItemService.create(request);

        assertEquals(99L, response.getId());
        assertEquals("Tên mới", response.getNameVn());
        assertEquals(7, response.getDisplayOrder());

        ArgumentCaptor<DictionaryItem> captor = ArgumentCaptor.forClass(DictionaryItem.class);
        verify(dictionaryItemRepository).save(captor.capture());
        DictionaryItem saved = captor.getValue();
        assertEquals(99L, saved.getId());
        assertFalse(saved.isDeleteFlg());
        assertEquals("TEST_GROUP", saved.getGroupCd());
        assertEquals("DEL_CD", saved.getItemCd());
    }

    @Test
    void createShouldThrowConflictWhenActivePairExists() {
        DictionaryItemCreateRequest request = new DictionaryItemCreateRequest();
        request.setGroupCd("TEST_GROUP");
        request.setItemCd("ACTIVE_CD");
        request.setNameVn("Tên mới");
        request.setNameEn("New name");

        when(dictionaryItemRepository.findByGroupCdAndItemCd("TEST_GROUP", "ACTIVE_CD"))
                .thenReturn(Optional.of(item(50L, "TEST_GROUP", "ACTIVE_CD", false)));

        BusinessException ex = assertThrows(BusinessException.class, () -> dictionaryItemService.create(request));
        assertEquals(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS, ex.getErrorCode());
        verify(dictionaryItemRepository, never()).save(any(DictionaryItem.class));
    }

    @Test
    void createShouldThrowConflictOnRaceConditionUniqueViolation() {
        DictionaryItemCreateRequest request = new DictionaryItemCreateRequest();
        request.setGroupCd("TEST_GROUP");
        request.setItemCd("RACE_CD");
        request.setNameVn("Tên mới");
        request.setNameEn("New name");

        when(dictionaryItemRepository.findByGroupCdAndItemCd("TEST_GROUP", "RACE_CD")).thenReturn(Optional.empty());
        when(dictionaryItemRepository.save(any(DictionaryItem.class)))
                .thenThrow(new DataIntegrityViolationException("uq_dictionary_items_group_item"));

        BusinessException ex = assertThrows(BusinessException.class, () -> dictionaryItemService.create(request));
        assertEquals(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void updateShouldChangeEditableFieldsOnly() {
        DictionaryItemUpdateRequest request = new DictionaryItemUpdateRequest();
        request.setNameVn("Tên sửa");
        request.setNameEn(null);
        request.setDisplayOrder(9);
        request.setActiveFlg(false);

        DictionaryItem existing = item(10L, "TEST_GROUP", "KEEP_CD", false);

        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(existing));
        when(dictionaryItemRepository.save(any(DictionaryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = dictionaryItemService.update(10L, request);

        assertEquals("Tên sửa", response.getNameVn());
        assertEquals("Old name", response.getNameEn());
        assertEquals(9, response.getDisplayOrder());
        assertFalse(response.getActiveFlg());
        assertEquals("TEST_GROUP", response.getGroupCd());
        assertEquals("KEEP_CD", response.getItemCd());
    }

    @Test
    void updateShouldThrowNotFoundWhenMissingOrDeleted() {
        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(404L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictionaryItemService.update(404L, new DictionaryItemUpdateRequest()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteShouldSoftDeleteExistingItem() {
        DictionaryItem existing = item(11L, "TEST_GROUP", "SOFT_CD", false);

        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(11L)).thenReturn(Optional.of(existing));
        when(dictionaryItemRepository.save(any(DictionaryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        dictionaryItemService.delete(11L);

        ArgumentCaptor<DictionaryItem> captor = ArgumentCaptor.forClass(DictionaryItem.class);
        verify(dictionaryItemRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleteFlg());
    }

    @Test
    void deleteShouldThrowNotFoundWhenMissing() {
        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(404L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> dictionaryItemService.delete(404L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void batchDeleteShouldSoftDeleteOnlyActiveIdsAndReturnCount() {
        DictionaryItem active1 = item(1L, "G", "A", false);
        DictionaryItem active2 = item(2L, "G", "B", false);

        DictionaryItemBatchDeleteRequest request = new DictionaryItemBatchDeleteRequest();
        request.setIds(List.of(1L, 2L, 3L));

        when(dictionaryItemRepository.findByIdInAndDeleteFlgFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(active1, active2));

        long deleted = dictionaryItemService.batchDelete(request);

        assertEquals(2, deleted);
        assertTrue(active1.isDeleteFlg());
        assertTrue(active2.isDeleteFlg());
        verify(dictionaryItemRepository).saveAll(List.of(active1, active2));
    }

    @Test
    void getDetailShouldReturnDtoWhenFound() {
        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(12L))
                .thenReturn(Optional.of(item(12L, "TEST_GROUP", "DETAIL_CD", false)));

        var response = dictionaryItemService.getDetail(12L);

        assertEquals(12L, response.getId());
        assertEquals("DETAIL_CD", response.getItemCd());
    }

    @Test
    void getEntityByIdAndGroupShouldReturnEntityWhenGroupMatches() {
        DictionaryItem entity = item(13L, "MATCH_GROUP", "CD_1", false);
        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(13L)).thenReturn(Optional.of(entity));

        DictionaryItem result = dictionaryItemService.getEntityByIdAndGroup(13L, "MATCH_GROUP");

        assertEquals(entity, result);
    }

    @Test
    void getEntityByIdAndGroupShouldThrowNotFoundWhenGroupMismatch() {
        DictionaryItem entity = item(14L, "OTHER_GROUP", "CD_2", false);
        when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(14L)).thenReturn(Optional.of(entity));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictionaryItemService.getEntityByIdAndGroup(14L, "EXPECTED_GROUP"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
