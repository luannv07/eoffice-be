package vn.edu.epu.eoffice_be.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;

import java.util.UUID;

public interface ChangeHistoryLogRepository extends JpaRepository<ChangeHistoryLog, UUID>, JpaSpecificationExecutor<ChangeHistoryLog> {
}
