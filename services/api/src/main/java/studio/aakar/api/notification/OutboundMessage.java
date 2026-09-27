package studio.aakar.api.notification;

import java.util.Map;
import java.util.UUID;

/**
 * A templated message to one recipient.
 *
 * @param channel {@code whatsapp}, {@code sms} or {@code email}
 * @param template provider template name, e.g. {@code order_confirmed}
 * @param to phone (E.164) or email address
 * @param payload template variables
 * @param orderId the order the message is about, when there is one (the portal's messages log filters on it)
 */
public record OutboundMessage(String channel, String template, String to, Map<String, Object> payload, UUID orderId) {

    public static final String ORDER_CONFIRMED = "order_confirmed";
    public static final String PRINTING_TIMELAPSE = "printing_timelapse";
    public static final String SHIPPED = "shipped";
    public static final String DELIVERED = "delivered";
    public static final String OTP = "otp";
    public static final String WHATSAPP = "whatsapp";
    public static final String SMS = "sms";
    public static final String EMAIL = "email";

    public OutboundMessage {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public OutboundMessage(String channel, String template, String to, Map<String, Object> payload) {
        this(channel, template, to, payload, null);
    }
}
