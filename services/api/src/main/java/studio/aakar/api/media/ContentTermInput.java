package studio.aakar.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code ContentTermInput} in the management contract, for {@code POST} and {@code PUT /admin/api/content-terms}. On PUT the
 * term is the rule's key and stays as stored: the body must name the same term (same letters and digits).
 */
public record ContentTermInput(
        @NotBlank(message = "term is required") @Size(max = 80, message = "term must be at most 80 characters") String term,
        @NotNull(message = "kind is required (trademark, character or other)") ContentTermKind kind,
        @Size(max = 200, message = "reason must be at most 200 characters") String reason,
        Boolean active) {

    /** {@code active} defaults to true. */
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
