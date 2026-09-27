package studio.aakar.api.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.aakar.api.payment.GatewayPayment;
import studio.aakar.api.payment.PaymentRequest;

class MockPaymentGatewayTest {

    @Test
    void payUrlPointsAtThePlaceholderPageAndRefIsMockPrefixed() {
        UUID paymentId = UUID.randomUUID();
        MockPaymentGateway gateway = new MockPaymentGateway("http://localhost:3000/");

        GatewayPayment payment = gateway.create(new PaymentRequest(paymentId, UUID.randomUUID(), "AK-000001", UUID.randomUUID(), 114_900, "INR"));

        assertThat(gateway.name()).isEqualTo("mock");
        assertThat(payment.payUrl()).isEqualTo("http://localhost:3000/checkout/pay/" + paymentId);
        assertThat(payment.gatewayRef()).matches("mock_[0-9a-f]{12}");
    }
}
