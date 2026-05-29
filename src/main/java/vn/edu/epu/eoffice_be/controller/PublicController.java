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
        // secretToken: sluannsdflksdnkf
        return "OK: " + new Date();
    }

    @GetMapping("/check-2")
    public String check2() {
        String token = "jwtokenhodaydldsjlfjdlksfjdlskfdsf";
        return "OK: " + new Date() + " " + token;
    }
}
