/**
 * Payments (PLAN §12, ADR-0013) through the {@link studio.aakar.api.payment.PaymentGateway} adapter. A payment
 * is created for an order awaiting payment and confirmed through {@code Payments.confirm}, the same path a real
 * gateway webhook takes; the mock gateway ({@code aakar.payments.gateway=mock}, default) is confirmed from the
 * placeholder pay page via {@code POST /api/payments/{id}/mock/complete}. Publishes
 * {@link studio.aakar.api.payment.PaymentSucceeded} / {@link studio.aakar.api.payment.PaymentFailed} for the
 * order and cart modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payment")
package studio.aakar.api.payment;
