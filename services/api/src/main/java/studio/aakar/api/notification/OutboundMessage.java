package studio.aakar.api.notification;

import java.util.Map;

/**
 * A templated message to one recipient.
 *
 * @param channel {@code whatsapp}, {@code sms} or {@code email}
 * @param template provider template name, e.g. {@code order_confirmed}
 * @param to phone (E.164) or email address
 * @param payload template variables
 */
public record OutboundMessage(String channel, String template, String to, Map<String, Object> payload) {

    public static final String ORDER_CONFIRMED = "order_confirmed";
    public static final String WHATSAPP = "whatsapp";
    public static final String SMS = "sms";

    public OutboundMessage {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
