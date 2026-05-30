package vn.edu.epu.eoffice_be.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/public")
public class PublicController {
    @GetMapping("/health")
    public String health() {
        return "OK: " + new Date();
    }
    @GetMapping("/test-v2-fixed")
    public String test(@RequestParam String username) {
        return "SELECT * FROM USERS where username = ? and password = ?";
    }

    @GetMapping("/test-collab")
    public String test2(@RequestParam String username) {
        return "SELECT * FROM USERS where username = ? and password = ?";
    }
    // lo secret
    @GetMapping("/test-secret")
    public String test3() {
        String apiKey = "apiKey12lkjslkdjfsd";
        String secretKey = "secret";
        return "jwtTOkenSecretabc89237894234";
    }
}
