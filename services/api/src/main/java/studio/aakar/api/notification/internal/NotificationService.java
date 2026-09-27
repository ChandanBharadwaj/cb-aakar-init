package studio.aakar.api.notification.internal;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.notification.MessageSender;
import studio.aakar.api.notification.NotificationDto;
import studio.aakar.api.notification.Notifications;
import studio.aakar.api.notification.OutboundMessage;

@Service
class NotificationService implements Notifications {

    static final String STATUS_FAILED = "failed";
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final MessageSender sender;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, MessageSender sender, Clock clock) {
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NotificationDto send(UUID userId, OutboundMessage message) {
        String status;
        try {
            status = sender.send(message);
        } catch (RuntimeException e) {
            // A provider outage must never roll back the order that triggered the message.
            log.warn("Message sender {} failed for {} '{}': {}", sender.name(), message.channel(), message.template(), e.toString());
            status = STATUS_FAILED;
        }
        return notifications.save(new NotificationEntity(userId, message.channel(), message.template(), message.to(), message.payload(),
                status, clock.instant())).toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> forUser(UUID userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationEntity::toDto).toList();
    }
}
