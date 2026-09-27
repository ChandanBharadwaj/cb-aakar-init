package studio.aakar.api.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A recorded outbound message. */
public record NotificationDto(UUID id, UUID userId, String channel, String template, String to, Map<String, Object> payload,
        String status, Instant createdAt) {
}
