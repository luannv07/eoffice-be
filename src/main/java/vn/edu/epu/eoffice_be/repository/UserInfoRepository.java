package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.UserInfo;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

	interface DrafterName {
		Long getUserId();
		String getUsername();
		String getFullName();
	}

	@Query("""
			SELECT u.id AS userId, u.username AS username, i.fullName AS fullName
			FROM UserLogin u LEFT JOIN UserInfo i ON i.userLogin = u AND i.deleteFlg = false
			WHERE u.id IN :userIds
			""")
	List<DrafterName> findDrafterNamesByUserIds(@Param("userIds") Collection<Long> userIds);

	Optional<UserInfo> findByUserLoginIdAndDeleteFlgFalse(Long userLoginId);

	Optional<UserInfo> findByUserLoginId(Long userLoginId);

	Optional<UserInfo> findByContactEmailAndDeleteFlgFalse(String contactEmail);

	Optional<UserInfo> findByEmployeeCodeAndDeleteFlgFalse(String employeeCode);
}
