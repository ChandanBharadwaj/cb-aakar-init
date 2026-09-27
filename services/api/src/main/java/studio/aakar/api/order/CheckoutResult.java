package studio.aakar.api.order;

import java.util.UUID;
import studio.aakar.api.payment.PaymentDto;

/** {@code CheckoutResult} in the OpenAPI document: the new order and where to pay. */
public record CheckoutResult(UUID orderId, String orderNumber, PaymentDto payment) {
}
