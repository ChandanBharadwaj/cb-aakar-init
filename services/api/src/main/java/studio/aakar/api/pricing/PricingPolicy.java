package studio.aakar.api.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pricing inputs (PLAN §7.10), copied from {@code packages/design-tokens/materials.json → pricing_policy}.
 * Money is integer paise.
 */
@Validated
@ConfigurationProperties(prefix = "aakar.pricing")
public record PricingPolicy(
        @NotBlank String version,
        long machineRatePaisePerHour,
        @NotNull Map<String, Long> finishingFeePaise,
        long packagingFeePaise,
        double marginPct,
        int roundToRupeesEndingIn,
        long shippingFlatPaise,
        long freeShippingAbovePaise,
        @NotBlank String shippingLabel) {

    public PricingPolicy {
        finishingFeePaise = finishingFeePaise == null ? Map.of() : Map.copyOf(finishingFeePaise);
        if (roundToRupeesEndingIn < 0 || roundToRupeesEndingIn > 9) {
            throw new IllegalArgumentException("aakar.pricing.round-to-rupees-ending-in must be a digit 0-9");
        }
    }

    public long finishingFeeFor(String finishClass) {
        return finishingFeePaise.getOrDefault(finishClass, 0L);
    }
}
