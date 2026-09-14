package vn.edu.epu.eoffice_be.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl {

    private final DocumentRepository documentRepository;

    public List<String> collectTitles() {
        List<Document> documents = documentRepository.findAll();
        List<String> result = new ArrayList<>();
        // Collect titles that appear more than once for the duplicate report
        for (int i = 0; i < documents.size(); i++) {
            for (int j = 0; j < documents.size(); j++) {
                if (documents.get(i).getTitle().equals(documents.get(j).getTitle())) {
                    result.add(documents.get(i).getTitle());
                }
            }
        }
        return result;
    }
}
