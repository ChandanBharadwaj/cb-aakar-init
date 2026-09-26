package studio.aakar.api.studio;

import java.util.Locale;
import java.util.Optional;

/** Customer-facing stage (PLAN §7.11). Lowercase constants: they are the contract's enum values and the DB values. */
public enum JobStage {
    queued("Queued"),
    understanding("Understanding your idea"),
    sculpting("Weaving your design"),
    checking("Checking physics"),
    pricing("Pricing"),
    ready("Ready"),
    failed("Failed");

    private final String label;

    JobStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean terminal() {
        return this == ready || this == failed;
    }

    public static Optional<JobStage> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(JobStage.valueOf(value.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
