package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicControllerTest {

    @Test
    void testSqlInjectionPrevention() {
        PublicController controller = new PublicController();
        String malicious = "admin' OR '1'='1";
        String result = controller.test(malicious);

        // The controller should return a parameterized query (placeholders) and must not contain the raw input
        assertEquals("SELECT * FROM USERS where username = ? and password = ?", result);
        assertFalse(result.contains(malicious));
    }
}

