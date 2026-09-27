package studio.aakar.api.order.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.identity.Users;
import studio.aakar.api.notification.Notifications;
import studio.aakar.api.notification.OutboundMessage;
import studio.aakar.api.payment.PaymentFailed;
import studio.aakar.api.payment.PaymentSucceeded;

/**
 * Applies payment outcomes to orders inside the confirming transaction, then records the confirmation
 * message ({@code order_confirmed}) for the customer.
 */
@Component
class PaymentResultListener {

    private final OrderLifecycle lifecycle;
    private final OrderItemRepository items;
    private final Users users;
    private final Notifications notifications;

    PaymentResultListener(OrderLifecycle lifecycle, OrderItemRepository items, Users users, Notifications notifications) {
        this.lifecycle = lifecycle;
        this.items = items;
        this.users = users;
        this.notifications = notifications;
    }

    @EventListener
    void onSucceeded(PaymentSucceeded payment) {
        lifecycle.paymentSucceeded(payment).ifPresent(order -> notifyConfirmed(order, payment));
    }

    @EventListener
    void onFailed(PaymentFailed payment) {
        lifecycle.paymentFailed(payment);
    }

    private void notifyConfirmed(OrderEntity order, PaymentSucceeded payment) {
        String phone = users.find(order.userId()).map(UserDto::phone).orElse(order.addressField("phone"));
        if (phone == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", order.id().toString());
        payload.put("order_number", order.number());
        payload.put("total_paise", order.totalPaise());
        payload.put("items_count", items.findByOrderIdOrderBySortOrderAsc(order.id()).stream().mapToInt(OrderItemEntity::qty).sum());
        payload.put("invoice_number", payment.invoiceNumber());
        if (order.eta() != null) {
            payload.put("eta", order.eta().toString());
        }
        String channel = order.notifyWhatsapp() ? OutboundMessage.WHATSAPP : OutboundMessage.SMS;
        notifications.send(order.userId(), new OutboundMessage(channel, OutboundMessage.ORDER_CONFIRMED, phone, payload, order.id()));
    }
}
