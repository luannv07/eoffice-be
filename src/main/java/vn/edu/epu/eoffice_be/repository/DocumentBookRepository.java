package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.entity.DocumentBook;

import java.util.List;
import java.util.Optional;

public interface DocumentBookRepository extends JpaRepository<DocumentBook, Long>, JpaSpecificationExecutor<DocumentBook> {

	boolean existsByBookCodeAndDeleteFlgFalse(String bookCode);

	boolean existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(Integer year, Long orgNodeId, Long documentTypeId);

	Optional<DocumentBook> findByIdAndDeleteFlgFalse(Long id);

	@Transactional
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT b FROM DocumentBook b WHERE b.id = :id AND b.deleteFlg = false")
	Optional<DocumentBook> findByIdForUpdate(@Param("id") Long id);

	Page<DocumentBook> findAllByDeleteFlgFalseAndIsClosedFalse(Pageable pageable);

	Page<DocumentBook> findAllByDeleteFlgFalseAndIsClosedFalseAndYear(Integer year, Pageable pageable);

	List<DocumentBook> findAllByDeleteFlgFalseAndIsClosedFalse();

	List<DocumentBook> findAllByDeleteFlgFalseAndIsClosedFalseAndYear(Integer year);
}
