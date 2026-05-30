package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void securityFilterChainShouldWrapExceptionAsIllegalStateException() throws Exception {
        SecurityConfig config = new SecurityConfig();
        HttpSecurity httpSecurity = mock(HttpSecurity.class);
        when(httpSecurity.csrf(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("boom"));

        assertThrows(IllegalStateException.class, () -> config.securityFilterChain(httpSecurity));
    }

    @Test
    void securityFilterChainShouldReturnBuiltChainWhenHttpSecurityBuilds() throws Exception {
        SecurityConfig config = new SecurityConfig();
        HttpSecurity httpSecurity = mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);

        when(httpSecurity.csrf(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.sessionManagement(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.formLogin(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(chain);

        SecurityFilterChain result = config.securityFilterChain(httpSecurity);
        org.junit.jupiter.api.Assertions.assertEquals(chain, result);
    }
}
