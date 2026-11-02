package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class AdminSimulationPropertiesTest {
    @ParameterizedTest @ValueSource(strings={"dev","test"})
    void permitsExplicitOptInOnlyInDevOrTest(String profile) {
        var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
        var properties = new AdminSimulationProperties(environment);
        assertFalse(properties.isAdminSimulationEnabled()); properties.setAdminSimulationEnabled(true);
        assertDoesNotThrow(properties::validate);
    }
    @ParameterizedTest @ValueSource(strings={"staging","prod","production","default"})
    void refusesOptInOutsideTestEnvironments(String profile) {
        var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
        var properties = new AdminSimulationProperties(environment); properties.setAdminSimulationEnabled(true);
        assertThrows(IllegalStateException.class, properties::validate);
    }
    @Test void rejectsMixedAndMissingProfiles() {
        var environment = new MockEnvironment(); var properties = new AdminSimulationProperties(environment);
        properties.setAdminSimulationEnabled(true); assertThrows(IllegalStateException.class, properties::validate);
        environment.setActiveProfiles("test","production"); assertThrows(IllegalStateException.class, properties::validate);
    }
}
