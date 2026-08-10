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

@Entity
@Table(name = "org_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    OrgNode parent;

    @Column(nullable = false, length = 50, unique = true)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(name = "type_cd", nullable = false, length = 50)
    String typeCd;

    @Column(name = "org_order")
    @Builder.Default
    Integer orgOrder = 0;

    @Column(name = "effective_date")
    Instant effectiveDate;

    @Column(name = "expiration_date")
    Instant expirationDate;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}