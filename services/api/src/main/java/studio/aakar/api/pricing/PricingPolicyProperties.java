package studio.aakar.api.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.pricing.*}: the <em>seed</em> policy only (ADR-0008). {@code V4__pricing_policies.sql} inserts the
 * same values as the first active row; the store falls back to this seed when the table holds no active policy.
 */
@Validated
@ConfigurationProperties(prefix = "aakar.pricing")
public record PricingPolicyProperties(
        @NotBlank String version,
        long machineRatePaisePerHour,
        @NotNull Map<String, Long> finishingFeePaise,
        long packagingFeePaise,
        double marginPct,
        int roundToRupeesEndingIn,
        long shippingFlatPaise,
        long freeShippingAbovePaise,
        @NotBlank String shippingLabel) {

    public PricingPolicy toPolicy() {
        return new PricingPolicy(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct,
                roundToRupeesEndingIn, shippingFlatPaise, freeShippingAbovePaise, shippingLabel);
    }
}
