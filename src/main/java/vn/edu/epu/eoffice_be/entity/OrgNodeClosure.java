package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.io.Serializable;

@Entity
@Table(name = "org_node_closures")
@IdClass(OrgNodeClosure.OrgNodeClosureId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeClosure extends BaseEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ancestor_id", nullable = false)
    OrgNode ancestor;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "descendant_id", nullable = false)
    OrgNode descendant;

    @Column(nullable = false)
    @Builder.Default
    int depth = 0;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrgNodeClosureId implements Serializable {
        Long ancestor;
        Long descendant;
    }
}