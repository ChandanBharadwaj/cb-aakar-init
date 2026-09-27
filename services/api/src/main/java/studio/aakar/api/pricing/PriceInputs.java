package studio.aakar.api.pricing;

import java.util.List;

/** Inputs to the {@link PriceCalculator}, decoupled from where estimates and materials are stored. */
public final class PriceInputs {

    /** The material-independent slicing estimate ({@code print-estimate.v1.json}). */
    public record PrintEstimate(int printSeconds, double extrudedVolumeCm3) {
    }

    /** The material being priced. */
    public record Material(String id, double densityGCm3, String finishClass, long ratePerGPaise) {
    }

    /**
     * What the piece is beyond its print: its outcome family (whose {@code family_rules} add a setup fee and a minimum)
     * and the bought-in hardware packed with each piece. {@link #NONE} prices the print alone, exactly as before
     * outcome families existed.
     */
    public record Context(String familyId, List<Hardware> hardware) {

        public static final Context NONE = new Context(null, List.of());

        public Context {
            hardware = hardware == null ? List.of() : List.copyOf(hardware);
        }
    }

    /** A bought-in part per piece: {@code qty} × {@code unitCostPaise} before the policy's hardware markup. */
    public record Hardware(String sku, String name, int qty, long unitCostPaise) {
    }

    private PriceInputs() {
    }
}
