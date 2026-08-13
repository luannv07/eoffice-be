package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    Optional<UserInfo> findByUserLoginIdAndDeleteFlgFalse(Long userLoginId);
}