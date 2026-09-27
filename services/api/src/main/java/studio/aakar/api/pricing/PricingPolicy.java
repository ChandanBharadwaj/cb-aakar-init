package studio.aakar.api.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Pricing inputs (PLAN §7.10, ADR-0008): one immutable, versioned policy. Stored as JSON in
 * {@code pricing_policies}; the active version is served by {@link PricingPolicyStore}. Money is integer paise.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PricingPolicy(
        String version,
        long machineRatePaisePerHour,
        Map<String, Long> finishingFeePaise,
        long packagingFeePaise,
        double marginPct,
        int roundToRupeesEndingIn,
        long shippingFlatPaise,
        long freeShippingAbovePaise,
        String shippingLabel) {

    public PricingPolicy {
        finishingFeePaise = finishingFeePaise == null ? Map.of() : Map.copyOf(finishingFeePaise);
        if (roundToRupeesEndingIn < 0 || roundToRupeesEndingIn > 9) {
            throw new IllegalArgumentException("round_to_rupees_ending_in must be a digit 0-9");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("A pricing policy needs a version");
        }
    }

    public long finishingFeeFor(String finishClass) {
        return finishingFeePaise.getOrDefault(finishClass, 0L);
    }

    /** A copy of this policy under a new version label. */
    public PricingPolicy withVersion(String newVersion) {
        return new PricingPolicy(newVersion, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct,
                roundToRupeesEndingIn, shippingFlatPaise, freeShippingAbovePaise, shippingLabel);
    }
}
