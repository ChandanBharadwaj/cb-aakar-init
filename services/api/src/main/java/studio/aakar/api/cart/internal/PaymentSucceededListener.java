package studio.aakar.api.cart.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import studio.aakar.api.payment.PaymentSucceeded;

/**
 * The cart is emptied when its owner's payment succeeds. Listening to the payment module's event (rather
 * than an order event) keeps {@code order → cart} the only dependency between those two modules.
 */
@Component
class PaymentSucceededListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededListener.class);

    private final CartService carts;

    PaymentSucceededListener(CartService carts) {
        this.carts = carts;
    }

    @EventListener
    void on(PaymentSucceeded event) {
        carts.clearForUser(event.userId());
        log.info("Cart of user {} emptied after payment {} for order {}", event.userId(), event.paymentId(), event.orderId());
    }
}
