package vn.edu.epu.eoffice_be.audit.repository;

import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChangeHistoryLogRepository extends JpaRepository<ChangeHistoryLog, UUID> {
}
