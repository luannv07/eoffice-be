package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.Specification;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Schema(description = "Filter danh sách user cho admin (dạng query param)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserFilterRequest extends BaseFilterRequest {

    @Schema(description = "Từ khóa chứa trong username hoặc email (không phân biệt hoa thường)")
    String keyword;

    @Schema(description = "Lọc theo vai trò: USER / ADMIN / SYSTEM_ADMIN")
    Role role;

    @Schema(description = "Lọc theo trạng thái: ACTIVE / LOCKED / DISABLED")
    String userStatusCd;

    @Schema(description = "Lọc theo trạng thái xóa mềm: null = chỉ user đang hoạt động, true = đã xóa, false = đang hoạt động")
    Boolean deleted;

    private static final Set<String> SORT_FIELDS = Set.of(
            "username", "email", "role", "userStatusCd", "createdDate", "updatedDate");

    @Override
    protected Set<String> getAllowedSortFields() {
        return SORT_FIELDS;
    }

    public Specification<UserLogin> toSpecification() {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (userStatusCd != null && !userStatusCd.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("userStatusCd")), userStatusCd.trim().toLowerCase()));
            }
            if (deleted != null) {
                predicates.add(cb.equal(root.get("deleteFlg"), deleted));
            } else {
                predicates.add(cb.equal(root.get("deleteFlg"), false));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
