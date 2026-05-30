package vn.edu.epu.eoffice_be;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class EofficeBeApplication {

    private static final Logger log = LoggerFactory.getLogger(EofficeBeApplication.class);

    public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		SpringApplication.run(EofficeBeApplication.class, args);
        log.info("Application started 1234 8 chickens office");
	}

}
