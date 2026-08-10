package vn.edu.epu.eoffice_be.auth.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtUserPrincipal implements Serializable {

    Long userId;

    String username;

    String role;

    List<String> permissions;
}