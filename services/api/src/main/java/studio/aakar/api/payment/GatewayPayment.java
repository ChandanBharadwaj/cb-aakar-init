package studio.aakar.api.payment;

/** The gateway's answer: its order/payment reference and the customer's pay URL. */
public record GatewayPayment(String gatewayRef, String payUrl) {
}
