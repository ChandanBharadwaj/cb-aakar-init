package studio.aakar.api.admin.internal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/** {@code PricingPolicy} in the management contract: the policy body without its version. */
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
        @NotBlank(message = "shipping_label is required") @Size(max = 60, message = "shipping_label must be at most 60 characters") String shippingLabel) {

    static PricingPolicyInput from(PricingPolicy policy) {
        return new PricingPolicyInput(policy.machineRatePaisePerHour(), policy.finishingFeePaise(), policy.packagingFeePaise(), policy.marginPct(),
                policy.roundToRupeesEndingIn(), policy.shippingFlatPaise(), policy.freeShippingAbovePaise(), policy.shippingLabel());
    }

    /** The domain policy under {@code version}; finishing fees must be non-negative (422 otherwise). */
    PricingPolicy toPolicy(String version) {
        finishingFeePaise.forEach((finish, fee) -> {
            if (fee == null || fee < 0) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        "finishing_fee_paise." + finish + " must be a non-negative integer");
            }
        });
        return new PricingPolicy(version, machineRatePaisePerHour, finishingFeePaise, packagingFeePaise, marginPct, roundToRupeesEndingIn,
                shippingFlatPaise, freeShippingAbovePaise, shippingLabel.trim());
    }
}
