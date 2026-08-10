package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MeResponse {

    Long id;
    String username;
    String displayName;
    String email;
    String avatarUrl;
    Role role;
    Instant passwordExpiredAt;
}