package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.Instant;

/**
 * Sự kiện uỷ quyền tạm thời quyền (permission_cd) từ người NHƯỢNG quyền (delegator) sang
 * người NHẬN quyền (delegatee) tại một đơn vị (org_node).
 *
 * <p><b>RÀNG BUỘC INSERT:</b> KHÔNG được phép cho bất kỳ service nào insert trực tiếp bảng này
 * ngoại trừ ĐÚNG 1 service xử lý sau bước approve của workflow. Mọi thay đổi hiệu lực quyền
 * phải đi qua cơ chế uỷ quyền đã được duyệt.</p>
 *
 * <p>Người nhượng quyền (delegator) phải đang thực sự sở hữu permissionCd tại orgNode thông qua
 * PositionAssignment + PositionPermission. Trạng thái này phải được RE-CHECK lại mỗi lần
 * Authorization Service kiểm tra quyền (real-time), không chỉ một lần khi tạo.</p>
 */
@Entity
@Table(name = "position_degs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PositionDeg extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_node_id", nullable = false)
    OrgNode orgNode;

    @Column(name = "permission_cd", nullable = false, length = 100)
    String permissionCd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delegator_user_id", nullable = false)
    UserLogin delegator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delegatee_user_id", nullable = false)
    UserLogin delegatee;

    @Column(name = "effective_from", nullable = false)
    Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    Instant effectiveTo;

    @Column(name = "actual_revoked_at")
    Instant actualRevokedAt;

    @Column(name = "revoke_reason_cd", length = 50)
    String revokeReasonCd;

    @Column(name = "approval_request_id")
    Long approvalRequestId;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
