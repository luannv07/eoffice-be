package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EligibleApproverResponse {

    Long userId;
    String username;
    String fullName;
    String email;
    String positionCd;
    String positionName;
    Long orgNodeId;
    String orgNodeName;
}
