package vn.edu.epu.eoffice_be.controller;

import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/public")
@Auditable
public class PublicController {
    @GetMapping("/test-base")
    public BaseResponse<String> health() {
			log.info("Test chạy pipeline: {}", Instant.now());
        return BaseResponse.success("OK: " + Instant.now());
    }
}
