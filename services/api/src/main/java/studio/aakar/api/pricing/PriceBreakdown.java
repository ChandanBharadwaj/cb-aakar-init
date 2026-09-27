package studio.aakar.api.pricing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * {@code price-breakdown.v1.json}: customer-facing price lines in integer paise. {@code familyId} names the outcome
 * family whose rules applied; {@code minimumSubtotalPaise} is present only when the family minimum lifted the subtotal.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PriceBreakdown(
        String currency,
        String materialId,
        double massG,
        int printSeconds,
        List<Line> lines,
        long subtotalPaise,
        long shippingPaise,
        String shippingLabel,
        long totalPaise,
        String policyVersion,
        String familyId,
        Long minimumSubtotalPaise) {

    @JsonCreator
    public PriceBreakdown {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** A print-only breakdown: no family, no minimum (the form every snapshot before outcome families has). */
    public PriceBreakdown(String currency, String materialId, double massG, int printSeconds, List<Line> lines, long subtotalPaise,
            long shippingPaise, String shippingLabel, long totalPaise, String policyVersion) {
        this(currency, materialId, massG, printSeconds, lines, subtotalPaise, shippingPaise, shippingLabel, totalPaise, policyVersion, null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(String code, String label, String detail, long amountPaise) {

        public static final String MATERIAL = "material";
        public static final String MACHINE_TIME = "machine_time";
        public static final String FINISHING = "finishing";
        public static final String PACKAGING = "packaging";
        public static final String HARDWARE = "hardware";
        public static final String SETUP = "setup";
    }
}
