package vn.edu.epu.eoffice_be.audit.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.audit.dto.request.ChangeHistoryLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.ChangeHistoryLogResponse;
import vn.edu.epu.eoffice_be.audit.service.ChangeHistoryLogQueryService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

@RestController
@RequestMapping("/change-history-logs")
@RequiredArgsConstructor
@Auditable
public class ChangeHistoryLogController {

    private final ChangeHistoryLogQueryService changeHistoryLogQueryService;

    @GetMapping
    public BaseResponse<Page<@NonNull ChangeHistoryLogResponse>> filter(
            @Valid @ModelAttribute ChangeHistoryLogFilterRequest request
    ) {
        return BaseResponse.success(changeHistoryLogQueryService.filter(request));
    }
}
