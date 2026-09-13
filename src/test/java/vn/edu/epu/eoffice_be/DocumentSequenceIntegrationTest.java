package vn.edu.epu.eoffice_be;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DocumentSequenceIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Autowired
    private OrgNodeRepository orgNodeRepository;

    @Test
    @Transactional
    @DisplayName("Kiểm tra chèn hơn 50 văn bản thành công với sequence allocationSize = 50 sau khi migrate dữ liệu cũ")
    void insertMoreThan50Documents_shouldSucceedWithoutIdCollision() {
        // 1. Xác minh dữ liệu cũ đã được migrate từ changelog (văn bản mẫu có id = 2)
        Optional<Document> existingSample = documentRepository.findById(2L);
        assertTrue(existingSample.isPresent(), "Văn bản mẫu đã seed từ migration phải tồn tại");
        assertEquals(2L, existingSample.get().getId());

        long initialCount = documentRepository.count();
        assertTrue(initialCount >= 1, "Cơ sở dữ liệu phải có ít nhất 1 văn bản cũ từ migration");

        // 2. Chuẩn bị thông tin người soạn thảo và đơn vị ban hành từ dữ liệu seed
        UserLogin drafter = userLoginRepository.findById(2001L)
                .orElseGet(() -> userLoginRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy UserLogin trong DB")));

        OrgNode issuingOrg = orgNodeRepository.findById(100L)
                .orElseGet(() -> orgNodeRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy OrgNode trong DB")));

        // 3. Tạo hơn 50 văn bản mới (60 văn bản để vượt qua ngưỡng block 50 của sequence allocation)
        int totalNewDocs = 60;
        List<Document> newDocs = new ArrayList<>();
        for (int i = 1; i <= totalNewDocs; i++) {
            Document doc = Document.builder()
                    .title("Văn bản thử nghiệm batch sequence #" + i)
                    .documentTypeCd("CONG_VAN")
                    .issuingOrg(issuingOrg)
                    .drafter(drafter)
                    .status(DocumentStatus.DRAFT)
                    .urgencyLevelCd("NORMAL")
                    .securityLevelCd("NORMAL")
                    .build();
            newDocs.add(doc);
        }

        // 4. Lưu toàn bộ 60 văn bản vào DB và flush để Hibernate thực thi cấp phát sequence và insert
        List<Document> savedDocs = documentRepository.saveAll(newDocs);
        documentRepository.flush();

        // 5. Kiểm tra kết quả
        assertEquals(totalNewDocs, savedDocs.size());

        Set<Long> generatedIds = savedDocs.stream()
                .map(Document::getId)
                .collect(Collectors.toSet());

        // Kiểm tra tính duy nhất: 60 ID phân biệt hoàn toàn
        assertEquals(totalNewDocs, generatedIds.size(), "Tất cả các ID sinh ra phải duy nhất, không trùng lặp");

        // Kiểm tra tính an toàn: không có ID nào null và tất cả ID đều lớn hơn ID cũ (2L)
        assertTrue(generatedIds.stream().allMatch(id -> id != null && id > existingSample.get().getId()),
                "Tất cả ID mới phải khác null và lớn hơn ID dữ liệu cũ (" + existingSample.get().getId() + ")");

        // Kiểm tra giá trị khởi đầu an toàn (>= 9950 theo cơ chế pooled optimizer của Hibernate với sequence = 10000)
        assertTrue(generatedIds.stream().allMatch(id -> id >= 9950L),
                "Tất cả ID mới phải bắt đầu từ giá trị an toàn của sequence (>= 9950)");

        // Kiểm tra văn bản thứ 50 và thứ 51 (vượt qua ranh giới block allocation 50)
        Document doc50 = savedDocs.get(49);
        Document doc51 = savedDocs.get(50);
        assertNotNull(doc50.getId(), "Văn bản thứ 50 phải có ID hợp lệ");
        assertNotNull(doc51.getId(), "Văn bản thứ 51 phải có ID hợp lệ");
        assertEquals(doc50.getId() + 1L, doc51.getId(), "ID văn bản thứ 51 phải kế tiếp liền kề văn bản thứ 50 qua ranh giới allocation");
        assertTrue(doc51.getId() > doc50.getId(), "ID văn bản thứ 51 phải lớn hơn văn bản thứ 50");

        // Kiểm tra tổng số lượng bản ghi trong DB
        long finalCount = documentRepository.count();
        assertEquals(initialCount + totalNewDocs, finalCount,
                "Tổng số lượng văn bản trong DB phải tăng đúng " + totalNewDocs);
    }
}
