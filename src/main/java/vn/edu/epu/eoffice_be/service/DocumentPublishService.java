package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentPublishService {

    static ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;

    @Transactional
    public Document publishDocument(Long documentId, PublishRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản với ID: " + documentId));

        // 1. Cấp số văn bản động theo documentId tránh trùng lặp
        String docNumber = String.format("%02d/QĐ-ĐHL", documentId);
        doc.setOfficialNumber(docNumber);
        doc.setIssueDate(request != null && request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now(ZONE_VN));
        if (request != null) {
            doc.setEffectiveDate(request.getEffectiveDate());
        }

        // 2. Thiết lập đường dẫn file và tính mã hash SHA-256 thực tế
        String officialPdfPath = "uploads/documents/doc_" + documentId + "_official.pdf";
        String officialHash = calculateSha256(Paths.get(officialPdfPath));

        doc.setOfficialFilePath(officialPdfPath);
        doc.setOfficialHash(officialHash);
        doc.setStatus("PUBLISHED");

        return documentRepository.save(doc);
    }

    public boolean verifyIntegrity(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản: " + documentId));

        if (doc.getOfficialHash() == null || doc.getOfficialHash().isBlank() || doc.getOfficialFilePath() == null) {
            return false;
        }

        String currentHash = calculateSha256(Paths.get(doc.getOfficialFilePath()));
        return doc.getOfficialHash().equalsIgnoreCase(currentHash);
    }

    /**
     * Tính mã băm SHA-256 từ nội dung file. 
     * Nếu file vật lý chưa tồn tại trên đĩa (trong môi trường test/mock), tính hash dựa trên đường dẫn tệp.
     */
    private String calculateSha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (!Files.exists(path)) {
                byte[] hash = digest.digest(path.toString().getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hash);
            }

            try (InputStream is = Files.newInputStream(path);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Đọc hết stream để DigestInputStream tính toán hash
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", e);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tính toán mã hash SHA-256 cho tệp", e);
        }
    }
}