// Truy vấn danh mục và khóa chức vụ khi cập nhật quyền.
package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, Long>, JpaSpecificationExecutor<DictionaryItem> {

	// Khóa chức vụ để tuần tự hóa các lần gán quyền cùng chức vụ.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT d FROM DictionaryItem d WHERE d.groupCd = 'POSITION_CD' AND d.itemCd = :positionCd AND d.deleteFlg = false")
	Optional<DictionaryItem> findPositionForPermissionUpdate(@Param("positionCd") String positionCd);

	List<DictionaryItem> findByGroupCdAndDeleteFlgFalseAndActiveFlgTrueOrderByDisplayOrderAsc(String groupCd);

	List<DictionaryItem> findByGroupCdInAndDeleteFlgFalseAndActiveFlgTrueOrderByGroupCdAscDisplayOrderAsc(Collection<String> groupCds);

	Optional<DictionaryItem> findByGroupCdAndItemCdAndDeleteFlgFalse(String groupCd, String itemCd);

	List<DictionaryItem> findByGroupCdAndItemCdInAndDeleteFlgFalse(String groupCd, Collection<String> itemCds);

	Optional<DictionaryItem> findByGroupCdAndItemCd(String groupCd, String itemCd);

	Optional<DictionaryItem> findByIdAndDeleteFlgFalse(Long id);

	List<DictionaryItem> findByIdInAndDeleteFlgFalse(Collection<Long> ids);
}
