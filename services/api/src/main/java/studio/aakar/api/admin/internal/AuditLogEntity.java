package studio.aakar.api.admin.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One staff write: who, when, what, and the value before and after as JSON. */
@Entity
@Table(name = "audit_log")
class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Instant at;
    @Column(name = "staff_email", nullable = false)
    private String staffEmail;
    @Column(nullable = false)
    private String action;
    @Column(nullable = false)
    private String target;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> before;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> after;

    protected AuditLogEntity() {
    }

    AuditLogEntity(Instant at, String staffEmail, String action, String target, Map<String, Object> before, Map<String, Object> after) {
        this.at = at;
        this.staffEmail = staffEmail;
        this.action = action;
        this.target = target;
        this.before = before;
        this.after = after;
    }

    AuditEntryDto toDto() {
        return new AuditEntryDto(id, at, staffEmail, action, target, before, after);
    }
}
