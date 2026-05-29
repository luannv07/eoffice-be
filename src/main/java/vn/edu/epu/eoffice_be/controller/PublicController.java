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
        // Return a parameterized query (use placeholders) instead of concatenating user input
        // This demonstrates using a prepared statement / parameterized query to prevent SQL injection.
        return "SELECT * FROM USERS where username = ? and password = ?";
    }
}
