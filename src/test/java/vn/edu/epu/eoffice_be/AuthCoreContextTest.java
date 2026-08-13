package vn.edu.epu.eoffice_be;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.JwtService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AuthCoreContextTest {

    @Autowired
    UserLoginRepository userLoginRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @Test
    @Transactional
    void contextLoadsAndJwtRoundTripWorks() {
        UserLogin user = UserLogin.builder()
                .username("ctx-admin")
                .passwordHash(passwordEncoder.encode("password"))
                .build();
        userLoginRepository.saveAndFlush(user);

        var found = userLoginRepository.findByUsernameAndDeleteFlgFalse("ctx-admin");
        assertTrue(found.isPresent());
        assertEquals("ctx-admin", found.get().getUsername());
        assertEquals("ACTIVE", found.get().getUserStatusCd());
        assertFalse(found.get().isMustChangePassword());
        assertNotNull(found.get().getId());

        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        var claims = jwtService.parseAccessToken(access);
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals("access", claims.get("token_type", String.class));
        assertEquals("eoffice-be-test", claims.getIssuer());

        var refreshClaims = jwtService.parseRefreshToken(refresh);
        assertEquals("refresh", refreshClaims.get("token_type", String.class));
    }
}