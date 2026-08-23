package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, Long>, JpaSpecificationExecutor<DictionaryItem> {

    List<DictionaryItem> findByGroupCdAndDeleteFlgFalseAndActiveFlgTrueOrderByDisplayOrderAsc(String groupCd);

    List<DictionaryItem> findByGroupCdInAndDeleteFlgFalseAndActiveFlgTrueOrderByGroupCdAscDisplayOrderAsc(Collection<String> groupCds);

    Optional<DictionaryItem> findByGroupCdAndItemCdAndDeleteFlgFalse(String groupCd, String itemCd);

    Optional<DictionaryItem> findByGroupCdAndItemCd(String groupCd, String itemCd);

    Optional<DictionaryItem> findByIdAndDeleteFlgFalse(Long id);

    List<DictionaryItem> findByIdInAndDeleteFlgFalse(Collection<Long> ids);
}
