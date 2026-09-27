package studio.aakar.api.notification;

/** Messaging adapter: WhatsApp, SMS or email depending on the channel. */
public interface MessageSender {

    /** Adapter name; {@code log} is refused by the production guard. */
    String name();

    /**
     * Sends (or, for the logging sender, only records) a message.
     *
     * @return the resulting status stored on the notification row: {@code sent}, {@code logged}, {@code failed}
     */
    String send(OutboundMessage message);
}
