package studio.aakar.api.pricing;

/** Inputs to the {@link PriceCalculator}, decoupled from where estimates and materials are stored. */
public final class PriceInputs {

    /** The material-independent slicing estimate ({@code print-estimate.v1.json}). */
    public record PrintEstimate(int printSeconds, double extrudedVolumeCm3) {
    }

    /** The material being priced. */
    public record Material(String id, double densityGCm3, String finishClass, long ratePerGPaise) {
    }

    private PriceInputs() {
    }
}
