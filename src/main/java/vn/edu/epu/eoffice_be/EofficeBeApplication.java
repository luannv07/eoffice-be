package vn.edu.epu.eoffice_be;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EofficeBeApplication {

	private static final Logger log = LoggerFactory.getLogger(EofficeBeApplication.class);

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication app = new SpringApplication(EofficeBeApplication.class);
		Environment env = app.run(args).getEnvironment();
		logApplicationStartup(env);
	}

	private static void logApplicationStartup(Environment env) {
		String protocol = env.getProperty("server.ssl.key-store") != null ? "https" : "http";
		String serverPort = env.getProperty("server.port");
		String contextPath = env.getProperty("server.servlet.context-path");
		if (contextPath == null || contextPath.isEmpty()) {
			contextPath = "/";
		}
		String hostAddress = "localhost";
		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("The host name could not be determined, using `localhost` as fallback");
		}
		String version = env.getProperty("spring.application.version");
		String swaggerPath = env.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
		String swaggerPrefix = "/".equals(contextPath) ? "" : contextPath;
		log.info("""
										
										----------------------------------------------------------
										\tApplication '{}' is running! Access URLs:
										\tLocal: \t\t{}://localhost:{}{}
										\tExternal: \t{}://{}:{}{}
										\tSwagger: \t{}://localhost:{}{}{}
										\tProfile(s): \t{}
										\tVersion: \t{}
										----------------------------------------------------------
										""",
						env.getProperty("spring.application.name"),
						protocol, serverPort, contextPath,
						protocol, hostAddress, serverPort, contextPath,
						protocol, serverPort, swaggerPrefix, swaggerPath,
						env.getActiveProfiles(),
						version != null ? version : "unknown");
	}
}
