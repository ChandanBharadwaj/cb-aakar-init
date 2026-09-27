package studio.aakar.api.media;

/**
 * What a {@link ContentScanner} says about a file: {@code clean} (usable at once), {@code flagged} (held for a human
 * reviewer; {@code reason} is shown to staff only) or {@code malware} (refused, never stored).
 */
public record ScanResult(Verdict verdict, String reason) {

    public enum Verdict {
        clean, flagged, malware
    }

    private static final ScanResult CLEAN = new ScanResult(Verdict.clean, null);

    public ScanResult {
        if (verdict == null) {
            throw new IllegalArgumentException("A scan result needs a verdict");
        }
    }

    public static ScanResult clean() {
        return CLEAN;
    }

    public static ScanResult flagged(String reason) {
        return new ScanResult(Verdict.flagged, reason == null || reason.isBlank() ? "Flagged by the content scanner" : reason);
    }

    public static ScanResult malware(String reason) {
        return new ScanResult(Verdict.malware, reason == null || reason.isBlank() ? "Malware signature" : reason);
    }

    public boolean isFlagged() {
        return verdict == Verdict.flagged;
    }

    public boolean isMalware() {
        return verdict == Verdict.malware;
    }
}
