package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.storage.DocumentPdfReference;
import java.util.*;

/** Document-linked file endpoints cannot bypass the document's access policy. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentFileReadGuard {
    private final StoredFileMetadataRepository metadata;
    private final DocumentRepository documents;
    private final DocumentReadPolicy policy;

    private List<Document> linked(String provider, String fileId) {
        var record = metadata.findByProviderAndFileId(provider, fileId);
        if (record.isEmpty()) return List.of();
        var meta = record.get();
        Map<Long, Document> found = new LinkedHashMap<>();
        for (var doc : documents.findReferencingFile(DocumentPdfReference.format(meta.getId()))) found.put(doc.getId(), doc);
        String id = meta.getMetadata() == null ? null : meta.getMetadata().get("documentId");
        if (id != null) {
            try {
                long documentId = Long.parseLong(id);
                var doc = documents.findByIdAndDeleteFlgFalse(documentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Tệp không có hồ sơ hợp lệ."));
                found.put(doc.getId(), doc);
            } catch (NumberFormatException e) { throw new BusinessException(ErrorCode.FORBIDDEN, "Liên kết hồ sơ của tệp không hợp lệ."); }
        }
        return List.copyOf(found.values());
    }
    public void assertRead(String provider, String fileId) {
        var linkedDocuments = linked(provider, fileId);
        if (linkedDocuments.isEmpty()) return;
        var user = policy.currentUser();
        if (DocumentAdminPolicy.isAdmin(user)) {
            policy.assertRead(linkedDocuments.getFirst(), user.getId());
            return;
        }
        var scoped = linkedDocuments.stream().filter(policy::applies).toList();
        if (scoped.isEmpty()) return;
        for (var doc : scoped) {
            try { policy.assertRead(doc, user.getId()); return; }
            catch (BusinessException e) { if (e.getErrorCode() != ErrorCode.FORBIDDEN) throw e; }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem văn bản này.");
    }
    public void assertUnboundWrite(String provider, String fileId) {
        var linkedDocuments = linked(provider, fileId);
        if (linkedDocuments.isEmpty()) return;
        boolean admin = DocumentAdminPolicy.isAdmin(policy.currentUser());
        if (admin || linkedDocuments.stream().anyMatch(policy::applies))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Tệp đã gắn với hồ sơ; vui lòng thao tác qua API văn bản.");
    }
    /** Bulk visibility evaluation; unrelated files retain their existing behavior. */
    public Set<String> visibleFileIds(String provider, Collection<String> fileIds) {
        Set<String> visible = new HashSet<>(fileIds);
        if (visible.isEmpty()) return visible;
        var records = metadata.findByProviderAndFileIdIn(provider, visible);
        if (records.isEmpty()) return visible;
        Map<String, Long> declared = new HashMap<>();
        Set<Long> documentIds = new HashSet<>();
        Set<String> references = new HashSet<>();
        for (var record : records) {
            references.add(DocumentPdfReference.format(record.getId()));
            String value = record.getMetadata() == null ? null : record.getMetadata().get("documentId");
            if (value != null) {
                try {
                    long id = Long.parseLong(value);
                    declared.put(record.getFileId(), id);
                    documentIds.add(id);
                } catch (NumberFormatException e) { visible.remove(record.getFileId()); }
            }
        }
        var linked = documents.findFileLinkedDocuments(documentIds, references);
        Map<Long, Document> byId = new HashMap<>();
        Map<String, List<Document>> byReference = new HashMap<>();
        for (var doc : linked) {
            byId.put(doc.getId(), doc);
            if (doc.getPrimaryFilePath() != null)
                byReference.computeIfAbsent(doc.getPrimaryFilePath(), k -> new ArrayList<>()).add(doc);
            if (doc.getOfficialFilePath() != null)
                byReference.computeIfAbsent(doc.getOfficialFilePath(), k -> new ArrayList<>()).add(doc);
        }
        Set<Long> readable = policy.readableIds(linked);
        for (var record : records) {
            List<Document> scoped = new ArrayList<>(byReference.getOrDefault(
                    DocumentPdfReference.format(record.getId()), List.of()));
            Long id = declared.get(record.getFileId());
            if (id != null) {
                if (!byId.containsKey(id)) { visible.remove(record.getFileId()); continue; }
                scoped.add(byId.get(id));
            }
            var protectedDocs = scoped.stream().filter(policy::applies).toList();
            if (!protectedDocs.isEmpty() && protectedDocs.stream().noneMatch(d -> readable.contains(d.getId())))
                visible.remove(record.getFileId());
        }
        return visible;
    }
}
