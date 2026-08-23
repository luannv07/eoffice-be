package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.dto.request.DictionaryItemBatchDeleteRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.DictionaryItemResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;

import java.util.List;

public interface DictionaryItemService {

    DictionaryItemResponse create(DictionaryItemCreateRequest request);

    DictionaryItemResponse update(Long id, DictionaryItemUpdateRequest request);

    void delete(Long id);

    long batchDelete(DictionaryItemBatchDeleteRequest request);

    org.springframework.data.domain.Page<DictionaryItemResponse> getAll(DictionaryItemFilterRequest request);

    DictionaryItemResponse getDetail(Long id);

    List<DictionaryItemResponse> lookupByGroups(List<String> groups);

    DictionaryItem getEntityByIdAndGroup(Long id, String groupCd);
}
