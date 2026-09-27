package studio.aakar.api.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * PLAN §7.10, extended for outcome families (plan §5):
 * <pre>
 * mass_g   = extruded_volume_cm3 × density_g_cm3
 * lines    = material (mass × rate) + machine_time (hours × machine_rate) + finishing[finish_class] + packaging (if > 0)
 *          + hardware (Σ qty × unit_cost × (1 + hardware_markup_pct / 100), when the piece packs any)
 *          + setup (family_rules[family].setup_fee_paise, when set)
 * subtotal = round_up_to_rupee_ending_in_9( Σ lines × (1 + margin_pct / 100) )
 * subtotal = max(subtotal, round_up_to_rupee_ending_in_9(family_rules[family].minimum_subtotal_paise))   → minimum_subtotal_paise
 * shipping = 0 when subtotal ≥ free_shipping_above, else the flat rate
 * total    = subtotal + shipping
 * </pre>
 * Every line is rounded to a whole rupee. Pure and deterministic given a policy and a {@link PriceInputs.Context}; the
 * overloads without a context price the print alone ({@link PriceInputs.Context#NONE}), so their numbers never moved,
 * and those without a policy use the store's active one (ADR-0008).
 */
@Component
public class PriceCalculator {

    static final String CURRENCY = "INR";
    static final String SETUP_LABEL = "Studio setup";
    private static final long PAISE_PER_RUPEE = 100;

    private final PricingPolicyStore policies;

    public PriceCalculator(PricingPolicyStore policies) {
        this.policies = policies;
    }

    /** The policy every new price is computed with. */
    public PricingPolicy policy() {
        return policies.active();
    }

    public PriceBreakdown price(PriceInputs.PrintEstimate estimate, PriceInputs.Material material) {
        return price(estimate, material, policies.active(), PriceInputs.Context.NONE);
    }

    public PriceBreakdown price(PriceInputs.PrintEstimate estimate, PriceInputs.Material material, PriceInputs.Context context) {
        return price(estimate, material, policies.active(), context);
    }

    public PriceBreakdown price(PriceInputs.PrintEstimate estimate, PriceInputs.Material material, PricingPolicy policy) {
        return price(estimate, material, policy, PriceInputs.Context.NONE);
    }

    public PriceBreakdown price(PriceInputs.PrintEstimate estimate, PriceInputs.Material material, PricingPolicy policy,
            PriceInputs.Context context) {
        PriceInputs.Context piece = context == null ? PriceInputs.Context.NONE : context;
        double massG = estimate.extrudedVolumeCm3() * material.densityGCm3();
        long materialPaise = wholeRupees(massG * material.ratePerGPaise());
        long machinePaise = wholeRupees(estimate.printSeconds() / 3600.0 * policy.machineRatePaisePerHour());
        long finishingPaise = wholeRupees(policy.finishingFeeFor(material.finishClass()));

        List<PriceBreakdown.Line> lines = new ArrayList<>();
        lines.add(new PriceBreakdown.Line(PriceBreakdown.Line.MATERIAL, "Material · " + Math.round(massG) + " g",
                material.id() + " · " + formatGrams(massG) + " g × ₹" + formatRupees(material.ratePerGPaise()) + "/g", materialPaise));
        lines.add(new PriceBreakdown.Line(PriceBreakdown.Line.MACHINE_TIME, "Print time · " + formatDuration(estimate.printSeconds()),
                "₹" + formatRupees(policy.machineRatePaisePerHour()) + " per printer hour", machinePaise));
        lines.add(new PriceBreakdown.Line(PriceBreakdown.Line.FINISHING, finishingLabel(material.finishClass()), null, finishingPaise));
        if (policy.packagingFeePaise() > 0) {
            lines.add(new PriceBreakdown.Line(PriceBreakdown.Line.PACKAGING, "Packaging · marigold box", null, wholeRupees(policy.packagingFeePaise())));
        }
        hardwareLine(piece.hardware(), policy).ifPresent(lines::add);
        PricingPolicy.FamilyRule rule = policy.familyRuleFor(piece.familyId());
        if (rule.setupFeePaise() > 0) {
            lines.add(new PriceBreakdown.Line(PriceBreakdown.Line.SETUP, SETUP_LABEL, null, wholeRupees(rule.setupFeePaise())));
        }

        long sum = lines.stream().mapToLong(PriceBreakdown.Line::amountPaise).sum();
        double withMargin = sum * (1 + policy.marginPct() / 100.0);
        long subtotal = roundUpToRupeeEndingIn(withMargin, policy.roundToRupeesEndingIn());
        Long minimumApplied = null;
        Optional<Long> minimum = minimumSubtotal(policy, piece.familyId());
        if (minimum.isPresent() && minimum.get() > subtotal) {
            subtotal = minimum.get();
            minimumApplied = minimum.get();
        }
        Shipping shipping = shipping(subtotal, policy);

        return new PriceBreakdown(CURRENCY, material.id(), round2(massG), estimate.printSeconds(), List.copyOf(lines),
                subtotal, shipping.paise(), shipping.label(), subtotal + shipping.paise(), policy.version(), piece.familyId(), minimumApplied);
    }

    /**
     * The family's minimum subtotal under the active policy, rounded like any subtotal; empty when the policy sets none.
     * It is a floor ("from ₹249"), not a quote: a larger piece prices above it.
     */
    public Optional<Long> minimumSubtotal(String familyId) {
        return minimumSubtotal(policies.active(), familyId);
    }

    /** {@link #minimumSubtotal(String)} under an explicit policy. */
    public static Optional<Long> minimumSubtotal(PricingPolicy policy, String familyId) {
        long minimum = policy.familyRuleFor(familyId).minimumSubtotalPaise();
        return minimum > 0 ? Optional.of(roundUpToRupeeEndingIn(minimum, policy.roundToRupeesEndingIn())) : Optional.empty();
    }

    /**
     * One line for everything bought in: Σ qty × unit cost × (1 + markup), labelled by name and count
     * ({@code Steel split ring 25 mm · 1}); empty when the piece packs nothing.
     */
    static Optional<PriceBreakdown.Line> hardwareLine(List<PriceInputs.Hardware> hardware, PricingPolicy policy) {
        List<PriceInputs.Hardware> parts = hardware == null ? List.of() : hardware.stream().filter(h -> h != null && h.qty() > 0).toList();
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        double cost = parts.stream().mapToDouble(h -> (double) h.qty() * h.unitCostPaise()).sum();
        long amount = wholeRupees(cost * (1 + policy.hardwareMarkupPct() / 100.0));
        String label = parts.stream().map(h -> (h.name() == null || h.name().isBlank() ? h.sku() : h.name()) + " · " + h.qty())
                .collect(Collectors.joining(" + "));
        String detail = parts.stream().map(h -> h.sku() + " × " + h.qty() + " at ₹" + formatRupees(h.unitCostPaise()))
                .collect(Collectors.joining(", ")) + (policy.hardwareMarkupPct() > 0 ? " + " + formatPercent(policy.hardwareMarkupPct()) + "% sourcing" : "");
        return Optional.of(new PriceBreakdown.Line(PriceBreakdown.Line.HARDWARE, label, detail, amount));
    }

    /** Shipping for an order subtotal: free at or above the policy threshold, otherwise the flat rate. */
    public static Shipping shipping(long subtotalPaise, PricingPolicy policy) {
        boolean free = subtotalPaise >= policy.freeShippingAbovePaise();
        return new Shipping(free ? 0 : policy.shippingFlatPaise(), free ? policy.shippingLabel() + " · Free" : policy.shippingLabel());
    }

    /** Shipping charge and its customer-facing label. */
    public record Shipping(long paise, String label) {
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

    private static String formatPercent(double pct) {
        return BigDecimal.valueOf(pct).stripTrailingZeros().toPlainString();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
