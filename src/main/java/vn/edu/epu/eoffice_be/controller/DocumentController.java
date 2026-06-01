package vn.edu.epu.eoffice_be.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

import java.time.Instant;

@RestController
@RequestMapping("/public")
@Auditable
public class DocumentController {
    @GetMapping("/test-base")
    public BaseResponse<String> health() {
        String secretKey = "123";
        return BaseResponse.success("OK: " + Instant.now());
    }
}
