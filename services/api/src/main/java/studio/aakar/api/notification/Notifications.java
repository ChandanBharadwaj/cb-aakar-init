package studio.aakar.api.notification;

import java.util.List;
import java.util.UUID;
import studio.aakar.api.shared.PageDto;

/** Public API of the notification module. */
public interface Notifications {

    /** Sends through the configured adapter and records the row with the status the adapter reports. */
    NotificationDto send(UUID userId, OutboundMessage message);

    List<NotificationDto> forUser(UUID userId);

    /** The messages log, newest first; {@code orderId} narrows it to one order when given. */
    PageDto<NotificationDto> page(UUID orderId, int page, int size);
}
