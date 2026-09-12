package vn.edu.epu.eoffice_be.service;

import org.springframework.data.domain.Page;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;

public interface DocumentBookService {

	Page<DocumentBookResponse> getAll(DocumentBookFilterRequest request);

	DocumentBookResponse getDetail(Long id);

	DocumentBookResponse create(DocumentBookCreateRequest request);

	DocumentBookResponse update(Long id, DocumentBookUpdateRequest request);

	void close(Long id);

	void reopen(Long id);

	void delete(Long id);

	AssignDocumentNumberResponse assignNumber(AssignDocumentNumberRequest request);
}
