package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

@RestController
@RequestMapping("/public/documents")
@RequiredArgsConstructor
@Tag(name = "Public Document Search", description = "Tra cuu van ban ban hanh cong khai - khong yeu cau dang nhap")
public class PublicDocumentController {

    private final DocumentQueryService queryService;

    @Operation(summary = "Tra cuu danh sach van ban da ban hanh")
    @GetMapping
    public BaseResponse<Page<DocumentResponse>> searchPublishedDocuments(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return BaseResponse.success(queryService.searchPublicDocuments(keyword, pageable));
    }

    @Operation(summary = "Xem chi tiet van ban da ban hanh")
    @GetMapping("/{id}")
    public BaseResponse<DocumentResponse> getPublishedDocumentDetail(@PathVariable Long id) {
        return BaseResponse.success(queryService.getPublicDocumentDetail(id));
    }
}