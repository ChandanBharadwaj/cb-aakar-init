package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.media.ContentTermDto;
import studio.aakar.api.media.ContentTermInput;
import studio.aakar.api.media.ContentTerms;
import studio.aakar.api.shared.ApiProblemException;

/**
 * Content rules (the portal's Content rules page): the names the studio won't print, the trademark guardrail behind Katha
 * (plan §8). Any staff role reads; the owner adds terms and changes their kind, reason and active switch, audited as
 * {@code content_term.create} / {@code content_term.update} with the term as the target.
 */
@RestController
@RequestMapping("/admin/api/content-terms")
@Tag(name = "admin · content rules")
@SecurityRequirement(name = "staffBearer")
class AdminContentTermsController {

    private final ContentTerms terms;
    private final AuditLog audit;

    AdminContentTermsController(ContentTerms terms, AuditLog audit) {
        this.terms = terms;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Content rules, active or not", description = "Every term in the order of its normalised form (folded, lowercase, "
            + "letters and digits only). An upload whose file name mentions an active term waits in the review queue; a design whose text "
            + "(Naam) mentions one is refused with 422 `protected_term`. Terms of six letters or digits or fewer (`whole_word`) only match "
            + "as a whole word. Any staff role.")
    List<ContentTermDto> all(StaffPrincipal staff) {
        return terms.all();
    }

    @PostMapping
    @Operation(summary = "Add a content rule", description = "Owner only. 409 `content_term_exists` when a term with the same letters and "
            + "digits exists (edit that one instead); 422 `validation_failed` for a term with fewer than two letters or digits. Audited as "
            + "`content_term.create`.")
    ResponseEntity<ContentTermDto> create(@Valid @RequestBody ContentTermInput input, StaffPrincipal staff) {
        staff.requireOwner();
        ContentTermDto created = terms.create(input);
        audit.record(staff.email(), AuditLog.CONTENT_TERM_CREATE, created.term(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{termId}")
    @Operation(summary = "Change a content rule's kind, reason or active switch", description = "Owner only. The term is the rule's key: "
            + "the body names the same term (same letters and digits), 422 `validation_failed` otherwise; add a new spelling as its own "
            + "term and switch this one off. 404 for an unknown id. Switching a term off stops it holding uploads and refusing names at "
            + "once. Audited as `content_term.update`.")
    ContentTermDto update(@PathVariable UUID termId, @Valid @RequestBody ContentTermInput input, StaffPrincipal staff) {
        staff.requireOwner();
        ContentTermDto before = terms.find(termId).orElseThrow(() -> ApiProblemException.notFound("Content term", termId));
        ContentTermDto after = terms.update(termId, input);
        audit.record(staff.email(), AuditLog.CONTENT_TERM_UPDATE, after.term(), before, after);
        return after;
    }
}
