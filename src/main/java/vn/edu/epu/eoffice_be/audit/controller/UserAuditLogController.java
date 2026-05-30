package vn.edu.epu.eoffice_be.audit.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.audit.dto.request.UserAuditLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.UserAuditLogResponse;
import vn.edu.epu.eoffice_be.audit.service.UserAuditLogQueryService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Auditable
public class UserAuditLogController {

    private final UserAuditLogQueryService userAuditLogQueryService;

    @GetMapping
    public BaseResponse<Page<@NonNull UserAuditLogResponse>> filter(
            @Valid @ModelAttribute UserAuditLogFilterRequest request
    ) {
        return BaseResponse.success(userAuditLogQueryService.filter(request));
    }
}
