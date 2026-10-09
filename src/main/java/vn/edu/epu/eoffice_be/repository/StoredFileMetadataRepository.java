package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;

import java.util.List;
import java.util.Optional;

public interface StoredFileMetadataRepository extends JpaRepository<StoredFileMetadata, Long> {

    Optional<StoredFileMetadata> findByProviderAndFileId(String provider, String fileId);

    List<StoredFileMetadata> findByProvider(String provider);
}
