package studio.aakar.api.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A recorded outbound message ({@code NotificationRecord} in the management contract). */
public record NotificationDto(UUID id, UUID orderId, UUID userId, String channel, String template, String to, Map<String, Object> payload,
        String renderedText, String status, Instant createdAt) {
}
