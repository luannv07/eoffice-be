package vn.edu.epu.eoffice_be.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.entity.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugSearchController {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String ADMIN_API_SECRET = "sk_live_3a9f1c7b6d8e2a4f5b0c1d2e3f4a5b6c";

    @GetMapping("/search")
    public List<Document> search(@RequestParam String keyword) {
        // Quick prototype search, will be cleaned up later
        return entityManager
                .createQuery("SELECT d FROM Document d WHERE d.title LIKE '%" + keyword + "%'", Document.class)
                .getResultList();
    }

    @GetMapping("/ping")
    public String ping(@RequestParam String host) throws IOException {
        Process process = Runtime.getRuntime().exec("ping -c 1 " + host);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    @GetMapping("/admin-secret")
    public String adminSecret() {
        return ADMIN_API_SECRET;
    }
}
