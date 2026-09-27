package studio.aakar.api.pricing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pricing inputs (PLAN §7.10, ADR-0008): one immutable, versioned policy. Stored as JSON in
 * {@code pricing_policies}; the active version is served by {@link PricingPolicyStore}. Money is integer paise.
 *
 * <p>Outcome categories (Avatars) add two data-only fields: {@link #hardwareMarkupPct()} on bought-in parts and
 * per-family {@link FamilyRule}s (minimum subtotal, setup fee, quantity breaks) keyed by family id. Policies
 * published before those fields existed read back with a 0 markup and no rules, so their prices never move.
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
        String shippingLabel,
        double hardwareMarkupPct,
        Map<String, FamilyRule> familyRules) {

    @JsonCreator
    public PricingPolicy {
        finishingFeePaise = finishingFeePaise == null ? Map.of() : Map.copyOf(finishingFeePaise);
        familyRules = copyRules(familyRules);
        if (roundToRupeesEndingIn < 0 || roundToRupeesEndingIn > 9) {
            throw new IllegalArgumentException("round_to_rupees_ending_in must be a digit 0-9");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("A pricing policy needs a version");
        }
        if (hardwareMarkupPct < 0) {
            throw new IllegalArgumentException("hardware_markup_pct must not be negative");
        }
    }

    /** The pre-Avatar nine-argument form: no hardware markup and no per-family rules. */
    public PricingPolicy(String version, long machineRatePaisePerHour, Map<String, Long> finishingFeePaise, long packagingFeePaise,
            double marginPct, int roundToRupeesEndingIn, long shippingFlatPaise, long freeShippingAbovePaise, String shippingLabel) {
        this(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct, roundToRupeesEndingIn, shippingFlatPaise,
                freeShippingAbovePaise, shippingLabel, 0, Map.of());
    }

    public long finishingFeeFor(String finishClass) {
        return finishingFeePaise.getOrDefault(finishClass, 0L);
    }

    /** The rule for a family, or an empty rule (no minimum, no setup fee, no breaks) when the policy names none. */
    public FamilyRule familyRuleFor(String familyId) {
        return familyId == null ? FamilyRule.NONE : familyRules.getOrDefault(familyId, FamilyRule.NONE);
    }

    /** A copy of this policy under a new version label. */
    public PricingPolicy withVersion(String newVersion) {
        return new PricingPolicy(newVersion, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct,
                roundToRupeesEndingIn, shippingFlatPaise, freeShippingAbovePaise, shippingLabel, hardwareMarkupPct, familyRules);
    }

    private static Map<String, FamilyRule> copyRules(Map<String, FamilyRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Map<String, FamilyRule> copy = new LinkedHashMap<>();
        rules.forEach((family, rule) -> {
            if (family != null && rule != null) {
                copy.put(family, rule);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Per-outcome pricing rule ({@code PricingPolicy.family_rules[id]} in the management contract). Zero means
     * "none", and zero values are left out of the JSON so a published policy reads like the seed.
     *
     * @param minimumSubtotalPaise lifts a piece's subtotal to at least this (rounded to the policy ending); 0 = none
     * @param setupFeePaise a studio setup line, e.g. repair and orientation labour for raw prints; 0 = none
     * @param qtyBreaks quantity discounts, deferred until the cart carries a discount line
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record FamilyRule(long minimumSubtotalPaise, long setupFeePaise, List<QtyBreak> qtyBreaks) {

        public static final FamilyRule NONE = new FamilyRule(0, 0, List.of());

        public FamilyRule {
            qtyBreaks = qtyBreaks == null ? List.of() : List.copyOf(qtyBreaks);
            if (minimumSubtotalPaise < 0 || setupFeePaise < 0) {
                throw new IllegalArgumentException("family rule amounts must not be negative");
            }
        }

        @JsonIgnore // a helper, not a property: otherwise an all-zero rule is stored as {"empty": true}
        public boolean isEmpty() {
            return minimumSubtotalPaise == 0 && setupFeePaise == 0 && qtyBreaks.isEmpty();
        }
    }

    /** {@code discount_pct} off the unit price from {@code min_qty} pieces of the same version. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QtyBreak(int minQty, double discountPct) {
    }
}
