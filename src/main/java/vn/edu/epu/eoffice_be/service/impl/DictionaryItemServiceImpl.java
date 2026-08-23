package vn.edu.epu.eoffice_be.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemBatchDeleteRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.DictionaryItemResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.service.DictionaryItemService;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DictionaryItemServiceImpl implements DictionaryItemService {

    DictionaryItemRepository dictionaryItemRepository;

    @Override
    @Transactional
    public DictionaryItemResponse create(DictionaryItemCreateRequest request) {
        java.util.Optional<DictionaryItem> existing =
                dictionaryItemRepository.findByGroupCdAndItemCd(request.getGroupCd(), request.getItemCd());

        DictionaryItem item;
        if (existing.isEmpty()) {
            item = DictionaryItem.builder()
                    .groupCd(request.getGroupCd())
                    .itemCd(request.getItemCd())
                    .nameVn(request.getNameVn())
                    .nameEn(request.getNameEn())
                    .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                    .activeFlg(request.getActiveFlg() != null ? request.getActiveFlg() : true)
                    .build();
        } else if (existing.get().isDeleteFlg()) {
            // Phục hồi dòng đã xóa mềm: giữ id/createdBy, ghi đè nội dung từ request
            item = existing.get();
            item.setDeleteFlg(false);
            item.setNameVn(request.getNameVn());
            item.setNameEn(request.getNameEn());
            item.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
            item.setActiveFlg(request.getActiveFlg() != null ? request.getActiveFlg() : true);
        } else {
            throw new BusinessException(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS);
        }

        try {
            dictionaryItemRepository.save(item);
        } catch (DataIntegrityViolationException e) {
            // guard chống race condition: cặp (group_cd, item_cd) vừa được tạo bởi request khác
            throw new BusinessException(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS);
        }
        return toDto(item);
    }

    @Override
    @Transactional
    public DictionaryItemResponse update(Long id, DictionaryItemUpdateRequest request) {
        DictionaryItem item = dictionaryItemRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        item.setNameVn(request.getNameVn());
        if (request.getNameEn() != null) {
            item.setNameEn(request.getNameEn());
        }
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActiveFlg() != null) {
            item.setActiveFlg(request.getActiveFlg());
        }
        return toDto(dictionaryItemRepository.save(item));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictionaryItem item = dictionaryItemRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        item.setDeleteFlg(true);
        dictionaryItemRepository.save(item);
    }

    @Override
    @Transactional
    public long batchDelete(DictionaryItemBatchDeleteRequest request) {
        var items = dictionaryItemRepository.findByIdInAndDeleteFlgFalse(request.getIds());
        items.forEach(item -> item.setDeleteFlg(true));
        dictionaryItemRepository.saveAll(items);
        return items.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DictionaryItemResponse> getAll(DictionaryItemFilterRequest request) {
        Specification<DictionaryItem> spec = (root, query, cb) -> cb.isFalse(root.get("deleteFlg"));

        if (request.getGroupCd() != null && !request.getGroupCd().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("groupCd")), "%" + request.getGroupCd().toLowerCase() + "%"));
        }
        if (request.getItemCd() != null && !request.getItemCd().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("itemCd")), "%" + request.getItemCd().toLowerCase() + "%"));
        }
        if (request.getNameVn() != null && !request.getNameVn().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nameVn")), "%" + request.getNameVn().toLowerCase() + "%"));
        }
        if (request.getNameEn() != null && !request.getNameEn().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nameEn")), "%" + request.getNameEn().toLowerCase() + "%"));
        }
        if (request.getActiveFlg() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("activeFlg"), request.getActiveFlg()));
        }

        Pageable pageable = request.pageable();
        return dictionaryItemRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DictionaryItemResponse getDetail(Long id) {
        return dictionaryItemRepository.findByIdAndDeleteFlgFalse(id)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictionaryItemResponse> lookupByGroups(List<String> groups) {
        if (groups == null || groups.isEmpty() || groups.stream().allMatch(g -> g == null || g.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Danh sách groups không được để trống (ví dụ: ?groups=USER_STATUS_CD,EMPLOYMENT_STATUS_CD)");
        }
        List<String> normalized = groups.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return dictionaryItemRepository
                .findByGroupCdInAndDeleteFlgFalseAndActiveFlgTrueOrderByGroupCdAscDisplayOrderAsc(normalized)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DictionaryItem getEntityByIdAndGroup(Long id, String groupCd) {
        DictionaryItem item = dictionaryItemRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.getGroupCd().equals(groupCd)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private DictionaryItemResponse toDto(DictionaryItem entity) {
        return DictionaryItemResponse.builder()
                .id(entity.getId())
                .groupCd(entity.getGroupCd())
                .itemCd(entity.getItemCd())
                .nameVn(entity.getNameVn())
                .nameEn(entity.getNameEn())
                .displayOrder(entity.getDisplayOrder())
                .activeFlg(entity.isActiveFlg())
                .createdDate(entity.getCreatedDate())
                .updatedDate(entity.getUpdatedDate())
                .build();
    }
}
