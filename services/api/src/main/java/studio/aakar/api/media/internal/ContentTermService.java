package studio.aakar.api.media.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.media.ContentTermDto;
import studio.aakar.api.media.ContentTermInput;
import studio.aakar.api.media.ContentTerms;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * The content rules behind the trademark guardrail ({@code content_terms}, V13). No cache: one small query per upload and per
 * design with text, so a term switched off in the portal stops holding uploads and refusing names at once, on every instance.
 */
@Service
class ContentTermService implements ContentTerms, ActiveTerms {

    /** {@code content_terms.term} and {@code normalised_term} are varchar(80). */
    static final int TERM_MAX = 80;
    /** A one-letter rule would refuse every lone initial. */
    static final int TERM_MIN_LETTERS = 2;
    private static final Comparator<ContentTermDto> MOST_SPECIFIC_FIRST = Comparator
            .comparingInt((ContentTermDto t) -> t.normalisedTerm().codePointCount(0, t.normalisedTerm().length())).reversed()
            .thenComparing(ContentTermDto::normalisedTerm);
    private static final Logger log = LoggerFactory.getLogger(ContentTermService.class);

    private final ContentTermRepository terms;
    private final Clock clock;

    ContentTermService(ContentTermRepository terms, Clock clock) {
        this.terms = terms;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentTermDto> all() {
        return terms.findAllByOrderByNormalisedTermAsc().stream().map(ContentTermEntity::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContentTermDto> find(UUID id) {
        return id == null ? Optional.empty() : terms.findById(id).map(ContentTermEntity::toDto);
    }

    @Override
    @Transactional
    public ContentTermDto create(ContentTermInput input) {
        String term = input.term().trim();
        String normalised = normalised(term);
        terms.findByNormalisedTerm(normalised).ifPresent(existing -> {
            throw exists(term, existing.term());
        });
        ContentTermEntity saved;
        try {
            saved = terms.saveAndFlush(new ContentTermEntity(term, normalised, input.kind(), input.reason(), input.activeOrDefault(), now()));
        } catch (DataIntegrityViolationException e) {
            throw exists(term, term); // created by someone else a moment ago
        }
        log.info("Content term \"{}\" ({}, {}) added; it matches as \"{}\"", saved.term(), input.kind(), input.activeOrDefault() ? "active" : "off",
                normalised);
        return saved.toDto();
    }

    @Override
    @Transactional
    public ContentTermDto update(UUID id, ContentTermInput input) {
        ContentTermEntity entity = terms.findById(id).orElseThrow(() -> ApiProblemException.notFound("Content term", id));
        if (!entity.normalisedTerm().equals(TermMatcher.normalise(input.term()))) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed", "A content rule's term can't be renamed: “"
                    + input.term().trim() + "” is not “" + entity.term() + "”. Add the new spelling as its own term and switch this one off");
        }
        entity.apply(input.kind(), input.reason(), input.activeOrDefault(), now());
        log.info("Content term \"{}\" updated ({}, {})", entity.term(), input.kind(), input.activeOrDefault() ? "active" : "off");
        return entity.toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContentTermDto> mentionedIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        TermMatcher.Text words = TermMatcher.text(text);
        return active().stream().filter(t -> words.mentions(t.normalisedTerm())).findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentTermDto> active() {
        return terms.findByActiveTrue().stream().map(ContentTermEntity::toDto).sorted(MOST_SPECIFIC_FIRST).toList();
    }

    /** The key a term is stored and compared under: at least two letters or digits, at most {@value #TERM_MAX}. */
    private static String normalised(String term) {
        String normalised = TermMatcher.normalise(term);
        int letters = normalised.codePointCount(0, normalised.length());
        if (letters < TERM_MIN_LETTERS) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "A content rule needs at least two letters or digits; “" + term + "” has " + (letters == 0 ? "none" : "one"));
        }
        if (letters > TERM_MAX) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "“" + term + "” is longer than " + TERM_MAX + " letters and digits once spaces and punctuation are set aside");
        }
        return normalised;
    }

    private static ApiProblemException exists(String term, String existing) {
        String detail = term.equals(existing) ? "“" + term + "” is already a content rule; edit it (or switch it back on) instead"
                : "“" + term + "” has the same letters and digits as the content rule “" + existing + "”; edit that one (or switch it back on) instead";
        return ApiProblemException.conflict(ProblemCodes.CONTENT_TERM_EXISTS, "Content term exists", detail);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS); // what PostgreSQL keeps, so the response equals every later read
    }
}
