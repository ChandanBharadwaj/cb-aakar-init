package studio.aakar.api.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.notification.NotificationDto;

@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String template;
    /** {@code to} is reserved in SQL, hence the quoting. */
    @Column(name = "\"to\"", nullable = false)
    private String recipient;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    NotificationEntity(UUID userId, String channel, String template, String recipient, Map<String, Object> payload, String status,
            Instant now) {
        this.userId = userId;
        this.channel = channel;
        this.template = template;
        this.recipient = recipient;
        this.payload = payload;
        this.status = status;
        this.createdAt = now;
    }

    NotificationDto toDto() {
        return new NotificationDto(id, userId, channel, template, recipient, payload, status, createdAt);
    }
}
