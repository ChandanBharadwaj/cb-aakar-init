package studio.aakar.api.media;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of a customer upload. Lowercase constants: contract enum values and DB values. A file the content scanner
 * flags waits in {@code pending_review} until a reviewer approves it ({@code ready}) or turns it down ({@code rejected}).
 */
public enum UploadStatus {
    ready, pending_review, rejected;

    public static Optional<UploadStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UploadStatus.valueOf(value.trim().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
