package studio.aakar.api.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.pricing.*}: the <em>seed</em> policy only (ADR-0008). {@code V4__pricing_policies.sql} and
 * {@code V9__families_hardware_uploads.sql} insert the same values as active rows; the store falls back to this seed
 * when the table holds no active policy. {@code family-rules} keys are family ids (bracketed in YAML so Boot keeps
 * their underscores).
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
        @NotBlank String shippingLabel,
        double hardwareMarkupPct,
        Map<String, PricingPolicy.FamilyRule> familyRules) {

    @ConstructorBinding
    public PricingPolicyProperties {
        familyRules = familyRules == null ? Map.of() : Map.copyOf(familyRules);
    }

    /** The pre-Avatar nine-argument form: no hardware markup and no per-family rules. */
    public PricingPolicyProperties(String version, long machineRatePaisePerHour, Map<String, Long> finishingFeePaise, long packagingFeePaise,
            double marginPct, int roundToRupeesEndingIn, long shippingFlatPaise, long freeShippingAbovePaise, String shippingLabel) {
        this(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct, roundToRupeesEndingIn, shippingFlatPaise,
                freeShippingAbovePaise, shippingLabel, 0, Map.of());
    }

    public PricingPolicy toPolicy() {
        return new PricingPolicy(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct,
                roundToRupeesEndingIn, shippingFlatPaise, freeShippingAbovePaise, shippingLabel, hardwareMarkupPct, familyRules);
    }
}
