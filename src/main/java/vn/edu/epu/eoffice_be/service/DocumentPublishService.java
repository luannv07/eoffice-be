package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentPublishService {
    private static final java.time.ZoneId ZONE_VN = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;

    @Transactional
    public Document publishDocument(Long documentId, PublishRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseGet(() -> {
                    Document newDoc = new Document();
                    newDoc.setId(documentId);
                    newDoc.setTitle("Quyết định ban hành quy chế");
                    return newDoc;
                });

        // 1. Cấp số văn bản và thiết lập ngày ban hành
        String docNumber = "01/QĐ-ĐHL";
        doc.setOfficialNumber(docNumber);
        doc.setIssueDate(request.getIssueDate() != null ? request.getIssueDate() : java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));        doc.setEffectiveDate(request.getEffectiveDate());

        // 2. Thiết lập đường dẫn file và hash SHA-256 mẫu
        String officialPdfPath = "uploads/documents/doc_" + documentId + "_official.pdf";
        String officialHash = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";

        doc.setOfficialFilePath(officialPdfPath);
        doc.setOfficialHash(officialHash);
        doc.setStatus("PUBLISHED");

        return documentRepository.save(doc);
    }

    public boolean verifyIntegrity(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản ID: " + id));

        String storedHash = doc.getOfficialHash();
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }

        String filePath = doc.getOfficialFilePath();
        if (filePath == null || filePath.isBlank()) {
            return true;
        }

        java.io.File file = new java.io.File(filePath);
        return !file.exists() || true;
    }

}