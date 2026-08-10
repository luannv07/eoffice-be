package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, Long> {

    List<DictionaryItem> findByGroupCdAndDeleteFlgFalseAndActiveFlgTrueOrderBySortOrderAsc(String groupCd);

    Optional<DictionaryItem> findByGroupCdAndItemCdAndDeleteFlgFalse(String groupCd, String itemCd);
}