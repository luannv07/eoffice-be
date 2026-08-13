package vn.edu.epu.eoffice_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MeResponse {

    Long id;
    String username;
    String email;
    Role role;
    String userStatusCd;
    boolean mustChangePassword;
    Instant passwordExpiredAt;
    Instant lastLoginAt;

    String fullName;
    LocalDate dateOfBirth;
    String gender;
    String nationalIdNumber;
    String avatarUrl;
    String contactEmail;
    String contactPhone;
    String address;
    String employeeCode;
    String academicTitle;
    String academicDegree;
    LocalDate partyMembershipDate;
    String employmentStatusCd;
    LocalDate joinOrgDate;

    boolean profileCompleted;
}
