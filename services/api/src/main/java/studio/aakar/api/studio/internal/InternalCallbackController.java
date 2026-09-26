package studio.aakar.api.studio.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.studio.Envelope;

/** Direct-profile callback target for the geometry service. Not exposed publicly (no CORS; front it accordingly). */
@RestController
@Tag(name = "internal")
class InternalCallbackController {

    private final GenerationResultApplier applier;

    InternalCallbackController(GenerationResultApplier applier) {
        this.applier = applier;
    }

    @PostMapping("/internal/jobs/{jobId}/callback")
    @Operation(summary = "Direct-profile callback from the geometry service",
            description = "Accepts a `design.progress`, `design.completed` or `design.failed` envelope. Idempotent on `event_id`.")
    ResponseEntity<Void> callback(@PathVariable UUID jobId, @Valid @RequestBody Envelope envelope) {
        if (envelope.type() == null || envelope.type().isBlank()) {
            throw ApiProblemException.validation("Envelope 'type' is required");
        }
        if (envelope.jobId() != null && !jobId.equals(envelope.jobId())) {
            throw ApiProblemException.validation("Envelope job_id " + envelope.jobId() + " does not match the path job " + jobId);
        }
        Envelope bound = envelope.jobId() == null
                ? new Envelope(envelope.type(), envelope.version(), envelope.eventId(), jobId, envelope.designId(),
                        envelope.occurredAt(), envelope.sequence(), envelope.payload())
                : envelope;
        applier.apply(bound);
        return ResponseEntity.noContent().build();
    }
}
