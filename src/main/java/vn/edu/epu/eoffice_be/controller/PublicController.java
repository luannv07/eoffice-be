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
    @GetMapping("/test")
    public String test(@RequestParam String usename) {
        // repository
        return "SELECT * FROM USERS where username = '" + usename + "'";
    }
}
