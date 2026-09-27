package studio.aakar.api.notification;

import java.util.List;
import java.util.UUID;

/** Public API of the notification module. */
public interface Notifications {

    /** Sends through the configured adapter and records the row with the status the adapter reports. */
    NotificationDto send(UUID userId, OutboundMessage message);

    List<NotificationDto> forUser(UUID userId);
}
