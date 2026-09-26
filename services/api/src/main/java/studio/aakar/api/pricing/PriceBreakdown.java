package studio.aakar.api.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** {@code price-breakdown.v1.json}: customer-facing price lines in integer paise. */
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
        String policyVersion) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(String code, String label, String detail, long amountPaise) {
    }
}
