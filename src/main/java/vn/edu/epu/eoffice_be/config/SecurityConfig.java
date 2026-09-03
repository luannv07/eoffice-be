package vn.edu.epu.eoffice_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.security.JwtAuthenticationFilter;
import vn.edu.epu.eoffice_be.security.ProfileCompletedFilter;
import vn.edu.epu.eoffice_be.security.RbacPermissionEvaluator;
import vn.edu.epu.eoffice_be.config.SecurityWhitelist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Whitelists centralized in SecurityWhitelist

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProfileCompletedFilter profileCompletedFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ProfileCompletedFilter profileCompletedFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.profileCompletedFilter = profileCompletedFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RbacPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    // JWT stateless APIs do not rely on cookie-based authentication.
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(Customizer.withDefaults())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(SecurityWhitelist.HEALTH_WHITELIST).permitAll()
                            .requestMatchers(SecurityWhitelist.SWAGGER_WHITELIST).permitAll()
                            .requestMatchers(SecurityWhitelist.PUBLIC_ENDPOINTS).permitAll()
                            .requestMatchers(SecurityWhitelist.REFRESH_TOKEN_ONLY_ENDPOINTS).permitAll()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) ->
                                    writeErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                            .accessDeniedHandler((request, response, accessDeniedException) ->
                                    writeErrorResponse(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN))
                    )
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(profileCompletedFilter, JwtAuthenticationFilter.class);

            return http.build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize Spring Security filter chain", ex);
        }
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, ErrorCode errorCode) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                BaseResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }
}
