package vn.edu.epu.eoffice_be;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EofficeBeMainTest {

	@Test
	void mainShouldSetDefaultTimezoneUtcAndRunSpringApplication() {
		TimeZone original = TimeZone.getDefault();
		try (MockedConstruction<SpringApplication> mocked = org.mockito.Mockito.mockConstruction(
						SpringApplication.class,
						(springApplication, context) -> {
							ConfigurableApplicationContext appContext = mock(ConfigurableApplicationContext.class);
							when(appContext.getEnvironment()).thenReturn(mock(ConfigurableEnvironment.class));
							when(springApplication.run(new String[]{})).thenReturn(appContext);
						}
		)) {
			EofficeBeApplication.main(new String[]{});

			assertEquals("UTC", TimeZone.getDefault().getID());
			assertEquals(1, mocked.constructed().size());
		} finally {
			TimeZone.setDefault(original);
		}
	}
}
