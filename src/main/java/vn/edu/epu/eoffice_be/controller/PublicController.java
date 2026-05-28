package vn.edu.epu.eoffice_be.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/public")
public class PublicController {
    @GetMapping("/health")
    public String health() {
        return "OK: " + new Date();
    }

    @GetMapping("/test-v3")
    public String testV2() {
        String secretToken = "daylasecrettoken";
        return "OK: " + secretToken;
    }
}
