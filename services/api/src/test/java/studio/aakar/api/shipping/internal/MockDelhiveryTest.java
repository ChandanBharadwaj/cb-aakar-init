package studio.aakar.api.shipping.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.aakar.api.shipping.CarrierShipment;
import studio.aakar.api.shipping.Serviceability;
import studio.aakar.api.shipping.ShipmentRequest;

class MockDelhiveryTest {

    private final MockDelhivery carrier = new MockDelhivery(Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void everyPincodeIsServiceableInFourDaysExceptNines() {
        Serviceability ok = carrier.serviceability("560001");
        assertThat(ok).isEqualTo(new Serviceability("560001", true, "mock-delhivery", 4, false));

        Serviceability nope = carrier.serviceability("900001");
        assertThat(nope.serviceable()).isFalse();
        assertThat(nope.carrier()).isEqualTo("mock-delhivery");
        assertThat(nope.etaDays()).isNull();
        assertThat(nope.codAvailable()).isNull();

        assertThat(carrier.serviceability("12").serviceable()).isFalse();
        assertThat(carrier.serviceability("056001").serviceable()).isFalse();
        assertThat(carrier.serviceability(null).serviceable()).isFalse();
        assertThat(carrier.serviceability(" 110001 ").serviceable()).isTrue();
    }

    @Test
    void shipmentsGetAMockAwbAndAnEta() {
        CarrierShipment shipment = carrier.createShipment(new ShipmentRequest(UUID.randomUUID(), "AK-000001", "Asha", "+919876543210",
                "12 MG Road", null, "Bengaluru", "Karnataka", "560001"));

        assertThat(shipment.carrier()).isEqualTo("mock-delhivery");
        assertThat(shipment.awb()).matches("MOCK\\d{10}");
        assertThat(shipment.trackingUrl()).isNull();
        assertThat(shipment.eta()).isEqualTo(LocalDate.of(2026, 10, 1)); // 27 Sep + 4 days in IST
    }
}
