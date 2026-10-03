package studio.aakar.api.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Content rules: the names the studio won't print (the trademark guardrail behind Katha, plan §8 and open decision 16),
 * edited in the portal and kept in {@code content_terms}. The {@link ContentScanner} holds an upload whose file name
 * mentions an active term for review; the design module refuses a text (Naam) that mentions one.
 *
 * <p>Matching ignores case, spaces and punctuation (text and term are folded and reduced to letters and digits, so
 * "iron man", "Iron-Man" and "IRONMAN" are one term). A term of six letters or digits or fewer must stand as a whole word of
 * the text ("DC" never catches "Adcock", "Thor" never "Thorat"); a longer one may sit anywhere, even run into other words.
 */
public interface ContentTerms {

    /** Every term, active or not, in the order of their normalised form (management API). */
    List<ContentTermDto> all();

    Optional<ContentTermDto> find(UUID id);

    /**
     * Adds a term. 409 {@code content_term_exists} when another term has the same letters and digits (switch that one on
     * instead); 422 {@code validation_failed} for a term with fewer than two letters or digits.
     */
    ContentTermDto create(ContentTermInput input);

    /**
     * Changes a term's kind, reason and active switch. The term itself is the rule's key: the input must name the same term
     * (same letters and digits; the stored spelling stays), 422 {@code validation_failed} otherwise. 404 for an unknown id.
     */
    ContentTermDto update(UUID id, ContentTermInput input);

    /** The most specific active term the text mentions (longest first), or empty when it mentions none. */
    Optional<ContentTermDto> mentionedIn(String text);
}
