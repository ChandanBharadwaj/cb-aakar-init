package studio.aakar.api.cart.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.pricing.PriceBreakdown;

/** Cart line rules: purchasable, repricing trigger, specs line, totals. */
class CartPricingTest {

    static final Map<String, Object> ESTIMATE = Map.of("print_seconds", 13200, "extruded_volume_cm3", 51.6);
    static final Map<String, Object> GEOMETRY = Map.of("bounds_mm", List.of(92, 78, 120.0));
    static final PriceBreakdown PRICE = new PriceBreakdown("INR", "terracotta_silk", 63.98, 13200, List.of(), 114_900, 0,
            "Shipping · Delhivery, 4 days · Free", 114_900, "2026-09-phase0");

    @Test
    void readyAndPassingWithAnEstimateIsPurchasable() {
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.ready, true, ESTIMATE)))).isTrue();
    }

    @Test
    void notReadyOrFailedPrintabilityOrMissingEstimateIsNot() {
        assertThat(CartPricing.purchasable(Optional.empty())).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.generating, true, ESTIMATE)))).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.failed, true, ESTIMATE)))).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.ready, false, ESTIMATE)))).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.ready, null, ESTIMATE)))).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.ready, true, null)))).isFalse();
        assertThat(CartPricing.purchasable(Optional.of(version(VersionStatus.ready, true, Map.of("print_seconds", 10))))).isFalse();
    }

    @Test
    void repriceOnlyWhenThePolicyVersionChanged() {
        assertThat(CartPricing.needsReprice("2026-09-phase0", "2026-09-phase0")).isFalse();
        assertThat(CartPricing.needsReprice("2026-09-phase0", "2026-10-v2")).isTrue();
        assertThat(CartPricing.needsReprice(null, "2026-10-v2")).isTrue();
    }

    @Test
    void specsLineReadsMaterialBoundsAndMass() {
        assertThat(CartPricing.specsLine("Terracotta Silk", version(VersionStatus.ready, true, ESTIMATE), PRICE))
                .isEqualTo("Terracotta Silk · 92 × 78 × 120 mm · 64 g");
        assertThat(CartPricing.specsLine("Indigo Matte", null, null)).isEqualTo("Indigo Matte");
        assertThat(CartPricing.specsLine(null, null, PRICE)).isEqualTo("64 g");
    }

    @Test
    void estimateAndThumbnailAreReadFromTheVersionDocuments() {
        DesignVersionResponse v = version(VersionStatus.ready, true, ESTIMATE);
        assertThat(CartPricing.estimate(v)).hasValueSatisfying(e -> {
            assertThat(e.printSeconds()).isEqualTo(13200);
            assertThat(e.extrudedVolumeCm3()).isEqualTo(51.6);
        });
        assertThat(CartPricing.thumbnailUrl(v)).isEqualTo("http://localhost:8081/assets/thumb.png");
        assertThat(CartPricing.thumbnailUrl(null)).isNull();
    }

    @Test
    void lineTotalIsTheUnitSubtotalTimesQuantity() {
        assertThat(CartPricing.lineTotal(PRICE, 3)).isEqualTo(344_700); // shipping is per cart, not per line
    }

    private static DesignVersionResponse version(VersionStatus status, Boolean passed, Map<String, Object> estimate) {
        Map<String, Object> printability = passed == null ? null : Map.of("passed", passed);
        Map<String, Object> assets = Map.of("thumb", Map.of("url", "http://localhost:8081/assets/thumb.png"));
        return new DesignVersionResponse(UUID.randomUUID(), UUID.randomUUID(), 1, null, status, Map.of("material", "terracotta_silk"), null,
                assets, GEOMETRY, printability, estimate, null, null, null, "user", Instant.now());
    }
}
