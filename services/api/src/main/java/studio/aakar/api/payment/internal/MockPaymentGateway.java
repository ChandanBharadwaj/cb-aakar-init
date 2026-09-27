package studio.aakar.api.payment.internal;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.payment.GatewayPayment;
import studio.aakar.api.payment.PaymentGateway;
import studio.aakar.api.payment.PaymentRequest;
import studio.aakar.api.shared.AakarProperties;

/**
 * ADR-0013 stand-in for Razorpay: the pay URL is the storefront's placeholder page
 * {@code {aakar.web.url}/checkout/pay/{paymentId}}, which reports the outcome through
 * {@code POST /api/payments/{id}/mock/complete}.
 */
@Component
@ConditionalOnProperty(name = "aakar.payments.gateway", havingValue = "mock", matchIfMissing = true)
class MockPaymentGateway implements PaymentGateway {

    static final String NAME = "mock";
    static final String REF_PREFIX = "mock_";
    static final String PAY_PATH = "/checkout/pay/";
    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String webUrl;

    MockPaymentGateway(AakarProperties properties) {
        this(properties.web().url());
    }

    MockPaymentGateway(String webUrl) {
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GatewayPayment create(PaymentRequest request) {
        byte[] id = new byte[6];
        RANDOM.nextBytes(id);
        String ref = REF_PREFIX + HexFormat.of().formatHex(id);
        String payUrl = webUrl + PAY_PATH + request.paymentId();
        log.info("[mock gateway] payment {} for order {} ({} paise) → {}", request.paymentId(), request.orderNumber(), request.amountPaise(), payUrl);
        return new GatewayPayment(ref, payUrl);
    }
}
