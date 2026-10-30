package vn.edu.epu.eoffice_be.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.util.Set;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.UserLogin;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "eoffice.workflow")
public class AdminSimulationProperties {
    private boolean adminSimulationEnabled = false;
    private final Environment environment;
    public AdminSimulationProperties(Environment environment) { this.environment = environment; }

    @PostConstruct
    public void validate() {
        if (!adminSimulationEnabled) return;
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0 || Arrays.stream(profiles).anyMatch(p -> !Set.of("dev", "test").contains(p)))
            throw new IllegalStateException("Admin simulation is only allowed with dev/test profiles");
    }
    public boolean allowed(UserLogin user) {
        return adminSimulationEnabled && user != null && isAdmin(user);
    }
    public static boolean isAdmin(UserLogin user) {
        return user != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN);
    }
}
