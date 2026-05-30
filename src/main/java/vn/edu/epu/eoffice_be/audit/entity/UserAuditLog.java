package vn.edu.epu.eoffice_be.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String username;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    String userAgent;

    @Column(name = "client_ip", length = 45)
    String clientIp;

    @Column(name = "request_uri")
    String requestUri;

    @Column(name = "method_name")
    String methodName;

    @Column(name = "execution_time_ms")
    Long executionTimeMs;

    @Column(name = "param_content", columnDefinition = "TEXT")
    String paramContent;

    @Column(name = "created_date", nullable = false, updatable = false)
    @CreationTimestamp
    Instant createdDate;
}
