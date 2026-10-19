package vn.edu.epu.eoffice_be.controller;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.config.RateLimitProperties;
import vn.edu.epu.eoffice_be.config.SecurityConfig;
import vn.edu.epu.eoffice_be.config.SecurityWhitelist;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.security.JwtAuthenticationFilter;
import vn.edu.epu.eoffice_be.security.ProfileCompletedFilter;
import vn.edu.epu.eoffice_be.security.RateLimitFilter;
import vn.edu.epu.eoffice_be.security.RbacPermissionEvaluator;
import vn.edu.epu.eoffice_be.service.JwtService;
import vn.edu.epu.eoffice_be.service.PkiCaService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises signed JWTs, the production filter chain and method-security proxies. */
@WebMvcTest(controllers = AdminPkiController.class, properties = {
        "eoffice.jwt.issuer=pki-security-test",
        "eoffice.jwt.access-token-ttl=15m"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ProfileCompletedFilter.class,
        RateLimitFilter.class, JwtService.class, JwtProperties.class, RateLimitProperties.class,
        AdminPkiControllerSecurityTest.SecurityTestBeans.class})
class AdminPkiControllerSecurityTest {

    private static final String STATUS = "/admin/pki/ca/status";
    private static final String INITIALIZE = "/admin/pki/ca/initialize";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    SecretKey secretKey;

    @MockitoBean
    PkiCaService pkiCaService;

    @MockitoBean
    RbacPermissionEvaluator permissionEvaluator;

    @Test
    void systemAdminWithoutProfileCanInspectStatus() throws Exception {
        when(pkiCaService.status()).thenReturn(new PkiCaStatusResponse(
                PkiCaStatusResponse.State.NOT_INITIALIZED, true, null, null, null, null));

        mockMvc.perform(get(STATUS).header(HttpHeaders.AUTHORIZATION, bearer(Role.SYSTEM_ADMIN, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("NOT_INITIALIZED"))
                .andExpect(jsonPath("$.data.canInitialize").value(true));

        verify(pkiCaService).status();
    }

    @Test
    void systemAdminWithoutProfileCanInitializeWithoutRequestBody() throws Exception {
        Instant now = Instant.now();
        when(pkiCaService.initialize()).thenReturn(new PkiCaStatusResponse(
                PkiCaStatusResponse.State.READY, false, "CN=Test Root CA", "a".repeat(64),
                now, now.plusSeconds(86400)));

        mockMvc.perform(post(INITIALIZE).header(HttpHeaders.AUTHORIZATION, bearer(Role.SYSTEM_ADMIN, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("READY"))
                .andExpect(jsonPath("$.data.canInitialize").value(false))
                .andExpect(jsonPath("$.data.subject").value("CN=Test Root CA"))
                .andExpect(jsonPath("$.data.fingerprintSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.privateKey").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.keystorePath").doesNotExist());

        verify(pkiCaService).initialize();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "SYSTEM_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotReadOrInitializeEvenWithoutProfile(Role role) throws Exception {
        String authorization = bearer(role, false);
        for (MockHttpServletRequestBuilder operation : new MockHttpServletRequestBuilder[]{get(STATUS), post(INITIALIZE)}) {
            mockMvc.perform(operation.header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }

        verifyNoInteractions(pkiCaService);
    }

    @Test
    void anonymousRequestsCannotReadOrInitialize() throws Exception {
        mockMvc.perform(get(STATUS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mockMvc.perform(post(INITIALIZE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(pkiCaService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "wrong-signature", "refresh"})
    void invalidBearerTokensCannotReadOrInitialize(String tokenKind) throws Exception {
        String token = switch (tokenKind) {
            case "expired" -> signedToken(secretKey, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
            case "wrong-signature" -> signedToken(Jwts.SIG.HS256.key().build(), Instant.now(), Instant.now().plusSeconds(60));
            case "refresh" -> jwtService.generateRefreshToken(1L, "system.admin", Role.SYSTEM_ADMIN);
            default -> "not-a-jwt";
        };

        for (MockHttpServletRequestBuilder operation : new MockHttpServletRequestBuilder[]{get(STATUS), post(INITIALIZE)}) {
            mockMvc.perform(operation.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        }

        verifyNoInteractions(pkiCaService);
    }

    @Test
    void incompleteProfileStillBlocksOtherAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/pki/ca/other")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.SYSTEM_ADMIN, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PROFILE_INCOMPLETE"));

        verifyNoInteractions(pkiCaService);
    }

    @Test
    void profileExemptionsAreLimitedToTheTwoExactMethodsAndPaths() {
        assertTrue(SecurityWhitelist.isProfileFilterExempt(STATUS, "GET"));
        assertTrue(SecurityWhitelist.isProfileFilterExempt(INITIALIZE, "POST"));
        assertFalse(SecurityWhitelist.isProfileFilterExempt(STATUS, "POST"));
        assertFalse(SecurityWhitelist.isProfileFilterExempt(INITIALIZE, "GET"));
        assertFalse(SecurityWhitelist.isProfileFilterExempt(STATUS + "/extra", "GET"));
        assertFalse(SecurityWhitelist.isProfileFilterExempt(INITIALIZE + "/extra", "POST"));
    }

    @ParameterizedTest
    @MethodSource("pkiErrors")
    void initializationPreservesTypedPkiErrors(ErrorCode errorCode) throws Exception {
        when(pkiCaService.initialize()).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post(INITIALIZE).header(HttpHeaders.AUTHORIZATION, bearer(Role.SYSTEM_ADMIN, true)))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @Test
    void statusPreservesStorageErrors() throws Exception {
        when(pkiCaService.status()).thenThrow(new BusinessException(ErrorCode.PKI_STORAGE_ERROR));

        mockMvc.perform(get(STATUS).header(HttpHeaders.AUTHORIZATION, bearer(Role.SYSTEM_ADMIN, true)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PKI_STORAGE_ERROR"));
    }

    private static Stream<ErrorCode> pkiErrors() {
        return Stream.of(ErrorCode.PKI_CA_ALREADY_EXISTS, ErrorCode.PKI_CA_RECOVERY_REQUIRED,
                ErrorCode.PKI_CA_NOT_INITIALIZED, ErrorCode.PKI_CA_EXPIRED,
                ErrorCode.PKI_STORAGE_ERROR, ErrorCode.PKI_USER_KEYSTORE_INVALID);
    }

    private String bearer(Role role, boolean profileCompleted) {
        return "Bearer " + jwtService.generateAccessToken(1L, "test.user", role, profileCompleted);
    }

    private String signedToken(SecretKey signingKey, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject("1")
                .issuer(jwtProperties.getIssuer())
                .claim("username", "system.admin")
                .claim("role", Role.SYSTEM_ADMIN.name())
                .claim("token_type", "access")
                .claim(JwtService.CLAIM_PROFILE_COMPLETED, false)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestBeans {

        @Bean
        SecretKey secretKey() {
            return Keys.hmacShaKeyFor("pki-controller-security-tests-only-32-bytes".getBytes(StandardCharsets.UTF_8));
        }

        @Bean("rateLimitCacheBuilder")
        Caffeine<Object, Object> rateLimitCacheBuilder() {
            return Caffeine.newBuilder().maximumSize(100);
        }
    }
}
