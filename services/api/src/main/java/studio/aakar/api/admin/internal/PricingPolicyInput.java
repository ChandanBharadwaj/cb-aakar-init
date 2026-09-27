package studio.aakar.api.admin.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * {@code PricingPolicy} in the management contract: the policy body without its version. {@code hardware_markup_pct}
 * and {@code family_rules} are optional (absent = 0 and none); zero amounts are left out on the way back so a
 * published version reads like the design-tokens seed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record PricingPolicyInput(
        @NotNull(message = "machine_rate_paise_per_hour is required") @Min(value = 0, message = "machine_rate_paise_per_hour must be at least 0")
        Long machineRatePaisePerHour,
        @NotNull(message = "finishing_fee_paise is required") Map<String, Long> finishingFeePaise,
        @NotNull(message = "packaging_fee_paise is required") @Min(value = 0, message = "packaging_fee_paise must be at least 0") Long packagingFeePaise,
        @NotNull(message = "margin_pct is required")
        @DecimalMin(value = "0", message = "margin_pct must be between 0 and 500")
        @DecimalMax(value = "500", message = "margin_pct must be between 0 and 500") Double marginPct,
        @NotNull(message = "round_to_rupees_ending_in is required")
        @Min(value = 0, message = "round_to_rupees_ending_in must be 0-9") @Max(value = 9, message = "round_to_rupees_ending_in must be 0-9")
        Integer roundToRupeesEndingIn,
        @NotNull(message = "shipping_flat_paise is required") @Min(value = 0, message = "shipping_flat_paise must be at least 0") Long shippingFlatPaise,
        @NotNull(message = "free_shipping_above_paise is required") @Min(value = 0, message = "free_shipping_above_paise must be at least 0")
        Long freeShippingAbovePaise,
        @NotBlank(message = "shipping_label is required") @Size(max = 60, message = "shipping_label must be at most 60 characters") String shippingLabel,
        @DecimalMin(value = "0", message = "hardware_markup_pct must be between 0 and 500")
        @DecimalMax(value = "500", message = "hardware_markup_pct must be between 0 and 500") Double hardwareMarkupPct,
        Map<String, @Valid FamilyRuleInput> familyRules) {

    private static final Pattern FAMILY_ID = Pattern.compile("^[a-z][a-z0-9_]*$");

    static PricingPolicyInput from(PricingPolicy policy) {
        Map<String, FamilyRuleInput> rules = new LinkedHashMap<>();
        policy.familyRules().forEach((family, rule) -> rules.put(family, FamilyRuleInput.from(rule)));
        return new PricingPolicyInput(policy.machineRatePaisePerHour(), policy.finishingFeePaise(), policy.packagingFeePaise(), policy.marginPct(),
                policy.roundToRupeesEndingIn(), policy.shippingFlatPaise(), policy.freeShippingAbovePaise(), policy.shippingLabel(),
                policy.hardwareMarkupPct(), rules.isEmpty() ? null : rules);
    }

    /** Family ids named by {@code family_rules}, for the controller to check against the catalog. */
    List<String> familyIds() {
        return familyRules == null ? List.of() : List.copyOf(familyRules.keySet());
    }

    /** The domain policy under {@code version}; finishing fees must be non-negative and rule keys family ids (422 otherwise). */
    PricingPolicy toPolicy(String version) {
        finishingFeePaise.forEach((finish, fee) -> {
            if (fee == null || fee < 0) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        "finishing_fee_paise." + finish + " must be a non-negative integer");
            }
        });
        Map<String, PricingPolicy.FamilyRule> rules = new LinkedHashMap<>();
        if (familyRules != null) {
            familyRules.forEach((family, rule) -> {
                if (family == null || !FAMILY_ID.matcher(family).matches()) {
                    throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                            "family_rules keys must be family ids (snake_case starting with a letter); got '" + family + "'");
                }
                if (rule == null) {
                    throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                            "family_rules." + family + " must be an object");
                }
                rules.put(family, rule.toRule());
            });
        }
        return new PricingPolicy(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct, roundToRupeesEndingIn,
                shippingFlatPaise, freeShippingAbovePaise, shippingLabel.trim(), hardwareMarkupPct == null ? 0 : hardwareMarkupPct, rules);
    }

    /** {@code PricingPolicy.family_rules} entry: every field optional, absent = 0 / none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FamilyRuleInput(
            @Min(value = 0, message = "minimum_subtotal_paise must be at least 0") Long minimumSubtotalPaise,
            @Min(value = 0, message = "setup_fee_paise must be at least 0") Long setupFeePaise,
            List<@Valid QtyBreakInput> qtyBreaks) {

        static FamilyRuleInput from(PricingPolicy.FamilyRule rule) {
            return new FamilyRuleInput(rule.minimumSubtotalPaise() == 0 ? null : rule.minimumSubtotalPaise(),
                    rule.setupFeePaise() == 0 ? null : rule.setupFeePaise(),
                    rule.qtyBreaks().isEmpty() ? null : rule.qtyBreaks().stream().map(b -> new QtyBreakInput(b.minQty(), b.discountPct())).toList());
        }

        PricingPolicy.FamilyRule toRule() {
            return new PricingPolicy.FamilyRule(minimumSubtotalPaise == null ? 0 : minimumSubtotalPaise, setupFeePaise == null ? 0 : setupFeePaise,
                    qtyBreaks == null ? List.of() : qtyBreaks.stream().map(QtyBreakInput::toBreak).toList());
        }
    }

    record QtyBreakInput(
            @NotNull(message = "min_qty is required") @Min(value = 2, message = "min_qty must be at least 2") Integer minQty,
            @NotNull(message = "discount_pct is required")
            @DecimalMin(value = "0", message = "discount_pct must be between 0 and 90")
            @DecimalMax(value = "90", message = "discount_pct must be between 0 and 90") Double discountPct) {

        PricingPolicy.QtyBreak toBreak() {
            return new PricingPolicy.QtyBreak(minQty, discountPct);
        }
    }
}
