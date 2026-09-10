package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "title", nullable = false)
    String title;

    @Column(name = "status", nullable = false, length = 30)
    String status;

    @Column(name = "official_number", length = 50)
    String officialNumber;

    @Column(name = "issue_date")
    LocalDate issueDate;

    @Column(name = "effective_date")
    LocalDate effectiveDate;

    @Column(name = "official_file_path")
    String officialFilePath;

    @Column(name = "official_hash", length = 64)
    String officialHash;
}

