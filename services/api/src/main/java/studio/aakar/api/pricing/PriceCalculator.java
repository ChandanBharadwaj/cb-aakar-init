package studio.aakar.api.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * PLAN §7.10:
 * <pre>
 * mass_g   = extruded_volume_cm3 × density_g_cm3
 * lines    = material (mass × rate) + machine_time (hours × machine_rate) + finishing[finish_class] + packaging (if > 0)
 * subtotal = round_up_to_rupee_ending_in_9( Σ lines × (1 + margin_pct / 100) )
 * shipping = 0 when subtotal ≥ free_shipping_above, else the flat rate
 * total    = subtotal + shipping
 * </pre>
 * Every line is rounded to a whole rupee. Pure and deterministic; the same inputs always price the same.
 */
@Component
public class PriceCalculator {

    static final String CURRENCY = "INR";
    private static final long PAISE_PER_RUPEE = 100;

    private final PricingPolicy policy;

    public PriceCalculator(PricingPolicy policy) {
        this.policy = policy;
    }

    public PricingPolicy policy() {
        return policy;
    }

    public PriceBreakdown price(PriceInputs.PrintEstimate estimate, PriceInputs.Material material) {
        double massG = estimate.extrudedVolumeCm3() * material.densityGCm3();
        long materialPaise = wholeRupees(massG * material.ratePerGPaise());
        long machinePaise = wholeRupees(estimate.printSeconds() / 3600.0 * policy.machineRatePaisePerHour());
        long finishingPaise = wholeRupees(policy.finishingFeeFor(material.finishClass()));

        List<PriceBreakdown.Line> lines = new ArrayList<>();
        lines.add(new PriceBreakdown.Line("material", "Material · " + Math.round(massG) + " g",
                material.id() + " · " + formatGrams(massG) + " g × ₹" + formatRupees(material.ratePerGPaise()) + "/g", materialPaise));
        lines.add(new PriceBreakdown.Line("machine_time", "Print time · " + formatDuration(estimate.printSeconds()),
                "₹" + formatRupees(policy.machineRatePaisePerHour()) + " per printer hour", machinePaise));
        lines.add(new PriceBreakdown.Line("finishing", finishingLabel(material.finishClass()), null, finishingPaise));
        if (policy.packagingFeePaise() > 0) {
            lines.add(new PriceBreakdown.Line("packaging", "Packaging · marigold box", null, wholeRupees(policy.packagingFeePaise())));
        }

        long sum = lines.stream().mapToLong(PriceBreakdown.Line::amountPaise).sum();
        double withMargin = sum * (1 + policy.marginPct() / 100.0);
        long subtotal = roundUpToRupeeEndingIn(withMargin, policy.roundToRupeesEndingIn());
        boolean freeShipping = subtotal >= policy.freeShippingAbovePaise();
        long shipping = freeShipping ? 0 : policy.shippingFlatPaise();
        String shippingLabel = freeShipping ? policy.shippingLabel() + " · Free" : policy.shippingLabel();

        return new PriceBreakdown(CURRENCY, material.id(), round2(massG), estimate.printSeconds(), List.copyOf(lines),
                subtotal, shipping, shippingLabel, subtotal + shipping, policy.version());
    }

    /** Nearest whole rupee, as paise. */
    static long wholeRupees(double paise) {
        return Math.round(paise / PAISE_PER_RUPEE) * PAISE_PER_RUPEE;
    }

    /** Rounds paise up to the nearest whole rupee whose last digit is {@code digit} (1242 → 1249, 1249 → 1249, 1250 → 1259). */
    static long roundUpToRupeeEndingIn(double paise, int digit) {
        long rupees = (long) Math.ceil(paise / PAISE_PER_RUPEE - 1e-9);
        long last = rupees % 10;
        if (last != digit) {
            rupees += last < digit ? digit - last : 10 - last + digit;
        }
        return rupees * PAISE_PER_RUPEE;
    }

    static String finishingLabel(String finishClass) {
        return "silk".equals(finishClass) ? "Hand sanding & sealing" : "Hand finishing";
    }

    static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = Math.round((seconds % 3600) / 60f);
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        return hours > 0 ? hours + " h " + minutes + " m" : minutes + " m";
    }

    private static String formatGrams(double g) {
        return BigDecimal.valueOf(g).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String formatRupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).stripTrailingZeros().toPlainString();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
