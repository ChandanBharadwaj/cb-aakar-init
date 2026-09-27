package studio.aakar.api.shipping.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.shared.ClockConfig;
import studio.aakar.api.shipping.CarrierShipment;
import studio.aakar.api.shipping.Serviceability;
import studio.aakar.api.shipping.ShipmentRequest;
import studio.aakar.api.shipping.ShippingCarrier;

/**
 * ADR-0013 stand-in for Delhivery: every valid 6-digit pincode is serviceable in {@link #ETA_DAYS} days without
 * COD, except pincodes starting with 9 (reserved for "not serviceable" tests and demos). Shipments get a
 * {@code MOCK} + 10-digit AWB and no tracking URL.
 */
@Component
@ConditionalOnProperty(name = "aakar.shipping.carrier", havingValue = "mock", matchIfMissing = true)
class MockDelhivery implements ShippingCarrier {

    static final String NAME = "mock-delhivery";
    static final int ETA_DAYS = 4;
    static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Logger log = LoggerFactory.getLogger(MockDelhivery.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    MockDelhivery(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Serviceability serviceability(String pincode) {
        String p = pincode == null ? "" : pincode.trim();
        if (!PINCODE.matcher(p).matches() || p.startsWith("9")) {
            return Serviceability.notServiceable(p, NAME);
        }
        return new Serviceability(p, true, NAME, ETA_DAYS, false);
    }

    @Override
    public CarrierShipment createShipment(ShipmentRequest request) {
        String awb = "MOCK" + String.format("%010d", Math.abs(RANDOM.nextLong() % 10_000_000_000L));
        LocalDate eta = LocalDate.now(clock.withZone(ClockConfig.STUDIO_ZONE)).plusDays(ETA_DAYS);
        log.info("[mock delhivery] booked {} for order {} to {} {}", awb, request.orderNumber(), request.city(), request.pincode());
        return new CarrierShipment(NAME, awb, eta, null);
    }
}
