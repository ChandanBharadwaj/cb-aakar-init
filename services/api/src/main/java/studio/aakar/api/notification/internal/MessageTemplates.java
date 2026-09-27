package studio.aakar.api.notification.internal;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import studio.aakar.api.notification.OutboundMessage;

/**
 * Renders the text a provider template would send, so the portal's messages log (ADR-0013) shows what the
 * customer would have read. Real providers render their own approved templates from the same payload.
 */
final class MessageTemplates {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private MessageTemplates() {
    }

    static String render(OutboundMessage message) {
        Map<String, Object> p = message.payload();
        return switch (message.template()) {
            case OutboundMessage.ORDER_CONFIRMED -> "Namaste! Order " + text(p, "order_number", "—") + " is confirmed. Total "
                    + rupees(p.get("total_paise")) + ". We'll message you as it moves through the studio.";
            case OutboundMessage.PRINTING_TIMELAPSE -> text(p, "title", "Your piece") + " is printing right now"
                    + (p.containsKey("layers_total") ? " (" + text(p, "layer", "1") + " of " + text(p, "layers_total", "?") + " layers)" : "")
                    + ". Watch it take shape: " + text(p, "tracking_url", "");
            case OutboundMessage.SHIPPED -> "Your Aakar piece is on its way with " + text(p, "carrier", "the carrier")
                    + (p.get("awb") == null ? "" : " (AWB " + p.get("awb") + ")") + ". Expected " + text(p, "eta", "in a few days") + ".";
            case OutboundMessage.DELIVERED -> "Delivered! Unbox, enjoy, and if you'd like another or a remix, scan the card in the box.";
            case OutboundMessage.OTP -> "Your Aakar sign-in code is " + text(p, "code", "······") + ". Valid for 5 minutes.";
            default -> message.template() + " " + p;
        };
    }

    static String rupees(Object paise) {
        if (!(paise instanceof Number n)) {
            return "₹—";
        }
        BigDecimal rupees = BigDecimal.valueOf(n.longValue()).movePointLeft(2);
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMaximumFractionDigits(rupees.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return "₹" + format.format(rupees);
    }

    private static String text(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
