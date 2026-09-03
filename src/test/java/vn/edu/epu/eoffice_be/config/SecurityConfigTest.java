package vn.edu.epu.eoffice_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import vn.edu.epu.eoffice_be.security.JwtAuthenticationFilter;
import vn.edu.epu.eoffice_be.security.ProfileCompletedFilter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private final JwtAuthenticationFilter jwtAuthenticationFilter = mock(JwtAuthenticationFilter.class);
    private final ProfileCompletedFilter profileCompletedFilter = mock(ProfileCompletedFilter.class);

    @Test
    void securityFilterChainShouldWrapExceptionAsIllegalStateException() throws Exception {
        SecurityConfig config = new SecurityConfig(jwtAuthenticationFilter, profileCompletedFilter, new ObjectMapper());
        HttpSecurity httpSecurity = mock(HttpSecurity.class);
        when(httpSecurity.csrf(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("boom"));

        assertThrows(IllegalStateException.class, () -> config.securityFilterChain(httpSecurity));
    }

    @Test
    void securityFilterChainShouldReturnBuiltChainWhenHttpSecurityBuilds() throws Exception {
        SecurityConfig config = new SecurityConfig(jwtAuthenticationFilter, profileCompletedFilter, new ObjectMapper());
        HttpSecurity httpSecurity = mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);

        when(httpSecurity.csrf(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.cors(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.sessionManagement(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.formLogin(org.mockito.ArgumentMatchers.any())).thenReturn(httpSecurity);
        when(httpSecurity.addFilterBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(httpSecurity);
        when(httpSecurity.addFilterAfter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(chain);

        SecurityFilterChain result = config.securityFilterChain(httpSecurity);
        org.junit.jupiter.api.Assertions.assertEquals(chain, result);
    }
}
