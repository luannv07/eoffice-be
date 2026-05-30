package vn.edu.epu.eoffice_be;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class EofficeBeMainTest {

    @Test
    void mainShouldSetDefaultTimezoneUtcAndRunSpringApplication() {
        TimeZone original = TimeZone.getDefault();
        try (MockedStatic<SpringApplication> springApplication = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            springApplication.when(() -> SpringApplication.run(EofficeBeApplication.class, new String[]{}))
                    .thenReturn(context);

            EofficeBeApplication.main(new String[]{});

            assertEquals("UTC", TimeZone.getDefault().getID());
            springApplication.verify(() -> SpringApplication.run(EofficeBeApplication.class, new String[]{}), times(1));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
