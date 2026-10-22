package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PositionAssignmentRepositoryTest {

	@Autowired
	PositionAssignmentRepository positionAssignmentRepository;

	@Autowired
	UserLoginRepository userLoginRepository;

	@Autowired
	OrgNodeRepository orgNodeRepository;

	@Test
	@DisplayName("existsOverlappingAssignment khi excludeId = null (tạo mới) hoạt động bình thường")
	void testExistsOverlappingAssignment_CreateMode_NullExcludeId() {
		Instant now = Instant.now();
		Instant from = now.minus(10, ChronoUnit.DAYS);
		Instant to = now.plus(10, ChronoUnit.DAYS);

		// Kiểm tra gọi hàm với excludeId = null không gây ra lỗi cú pháp hay suy luận kiểu dữ liệu
		assertDoesNotThrow(() -> {
			boolean exists = positionAssignmentRepository.existsOverlappingAssignment(999999L, from, to, null);
			assertFalse(exists);
		});
	}

	@Test
	@DisplayName("existsOverlappingAssignment khi excludeId != null (cập nhật) hoạt động bình thường")
	void testExistsOverlappingAssignment_UpdateMode_NonNullExcludeId() {
		Instant now = Instant.now();
		Instant from = now.minus(10, ChronoUnit.DAYS);
		Instant to = now.plus(10, ChronoUnit.DAYS);

		assertDoesNotThrow(() -> {
			boolean exists = positionAssignmentRepository.existsOverlappingAssignment(999999L, from, to, 12345L);
			assertFalse(exists);
		});
	}
}
