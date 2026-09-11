package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.dto.IntegrityVerifyResponse;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;
import jakarta.validation.Valid;
import java.time.Instant;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentPublishController {

    DocumentPublishService publishService;

    // UC15: Phát hành văn bản
    @PostMapping("/{id}/publish")
    public ResponseEntity<Document> publishDocument(
            @PathVariable Long id,
            @Valid @RequestBody PublishRequest request) {
        Document doc = publishService.publishDocument(id, request);
        return ResponseEntity.ok(doc);
    }
    
    // Trọng tâm DevSecOps: Verify tính toàn vẹn file văn bản
    @GetMapping("/{id}/verify-integrity")
    public ResponseEntity<IntegrityVerifyResponse> verifyIntegrity(@PathVariable Long id) {
        boolean isValid = publishService.verifyIntegrity(id);
        IntegrityVerifyResponse response = IntegrityVerifyResponse.builder()
                .valid(isValid)
                .documentId(id)
                .verifiedAt(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}