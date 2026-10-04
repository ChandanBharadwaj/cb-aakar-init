package studio.aakar.api.media;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code ContentTerm} in the management contract: a name the studio won't print (the trademark guardrail behind Katha,
 * plan §8). {@code normalisedTerm} is how the matcher compares it (folded, lowercase, letters and digits only);
 * {@code wholeWord} is true for short terms (six letters or digits or fewer), which only match as a whole word of a text.
 *
 * @param reason why it is protected, for staff ("Marvel character (Disney)"); null when none was given
 */
public record ContentTermDto(
        UUID id,
        String term,
        ContentTermKind kind,
        String reason,
        boolean active,
        String normalisedTerm,
        boolean wholeWord,
        Instant createdAt,
        Instant updatedAt) {
}
