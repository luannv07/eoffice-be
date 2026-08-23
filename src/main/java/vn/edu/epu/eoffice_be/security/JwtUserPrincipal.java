package vn.edu.epu.eoffice_be.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtUserPrincipal implements Principal, Serializable {

    Long userId;

    String username;

    String role;

    List<String> permissions;

    @Override
    public String getName() {
        return username;
    }
}