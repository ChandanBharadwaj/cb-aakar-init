package studio.aakar.api.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.notification.MessageSender;
import studio.aakar.api.notification.OutboundMessage;

/** ADR-0013: nothing leaves the building; the row in {@code notifications} is what would have been sent. */
@Component
@ConditionalOnProperty(name = "aakar.messaging.sender", havingValue = "log", matchIfMissing = true)
class LoggingMessageSender implements MessageSender {

    static final String NAME = "log";
    static final String STATUS_LOGGED = "logged";
    private static final Logger log = LoggerFactory.getLogger(LoggingMessageSender.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String send(OutboundMessage message) {
        log.info("[log sender] {} '{}' to {}: {}", message.channel(), message.template(), message.to(), message.payload());
        return STATUS_LOGGED;
    }
}
