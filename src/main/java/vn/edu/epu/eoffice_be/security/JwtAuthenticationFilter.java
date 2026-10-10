package vn.edu.epu.eoffice_be.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.service.JwtService;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String BEARER_PREFIX = "Bearer ";

	final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			try {
				Claims claims = jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
				setAuthentication(request, claims);
			} catch (JwtException | IllegalArgumentException ex) {
				log.debug("Invalid JWT: {}", ex.getMessage());
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	private void setAuthentication(HttpServletRequest request, Claims claims) {
		Long userId = Long.valueOf(claims.getSubject());
		String username = claims.get("username", String.class);
		if (username == null || username.isBlank()) {
			username = claims.getSubject();
		}
		Role role = Role.from(claims.get("role", String.class), Role.USER);
		Boolean profileCompleted = claims.get(JwtService.CLAIM_PROFILE_COMPLETED, Boolean.class);

		Object orgIdClaim = claims.get(JwtService.CLAIM_ORG_ID);
		Long orgId = null;
		if (orgIdClaim instanceof Number num) {
			orgId = num.longValue();
		} else if (orgIdClaim instanceof String str && !str.isBlank()) {
			try {
				orgId = Long.valueOf(str);
			} catch (NumberFormatException ignored) {
			}
		}

		JwtUserPrincipal principal = new JwtUserPrincipal(userId, username, role.name(), Boolean.TRUE.equals(profileCompleted), List.of(), orgId);

		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

		UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(principal, null, authorities);
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
