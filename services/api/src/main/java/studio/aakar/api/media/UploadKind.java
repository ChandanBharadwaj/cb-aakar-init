package studio.aakar.api.media;

import java.util.Locale;
import java.util.Optional;

/** What a customer upload is: a photo for a relief ({@code image}) or a model file for a hero form or Swaroop ({@code model}). */
public enum UploadKind {
    image, model;

    /** The kind for a multipart {@code kind} field, or empty when it is neither {@code image} nor {@code model}. */
    public static Optional<UploadKind> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UploadKind.valueOf(value.trim().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
