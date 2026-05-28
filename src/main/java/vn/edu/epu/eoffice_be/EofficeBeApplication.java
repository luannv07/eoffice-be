package vn.edu.epu.eoffice_be;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EofficeBeApplication {

    private static final Logger log = LoggerFactory.getLogger(EofficeBeApplication.class);

    public static void main(String[] args) {
		SpringApplication.run(EofficeBeApplication.class, args);
        log.info("✅ EofficeBeApplication started successfully");
	}

}
