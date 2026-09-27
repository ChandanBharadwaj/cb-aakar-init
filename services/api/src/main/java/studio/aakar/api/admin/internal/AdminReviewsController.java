package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.identity.Users;
import studio.aakar.api.media.StaffUpload;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.media.UploadReview;
import studio.aakar.api.media.UploadStatus;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * The content review queue (plan §4, ADR-0014): customer uploads newest first with their owner and review, and the
 * decision on a flagged one. Any staff role may decide (the studio reviews; open decision 6); every decision is audited
 * as {@code review.decide}.
 */
@RestController
@RequestMapping("/admin/api")
@Tag(name = "admin · reviews")
@SecurityRequirement(name = "staffBearer")
class AdminReviewsController {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final Uploads uploads;
    private final Users users;
    private final AuditLog audit;

    AdminReviewsController(Uploads uploads, Users users, AuditLog audit) {
        this.uploads = uploads;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/uploads")
    @Operation(summary = "Customer uploads, newest first (filter by status for the review queue)", description = "`status` is `ready`, "
            + "`pending_review` or `rejected`; `limit` 1–200 (default 50). Each upload carries its owner (with the phone of a signed-in "
            + "customer), its origin, its latest content review and a `url` staff can open whatever the status.")
    List<AdminUploadDto> uploads(@RequestParam(required = false) String status, @RequestParam(required = false) Integer limit, StaffPrincipal staff) {
        UploadStatus filter = status == null || status.isBlank() ? null : UploadStatus.parse(status).orElseThrow(() -> ApiProblemException.unprocessable(
                ProblemCodes.VALIDATION_FAILED, "Validation failed", "status must be ready, pending_review or rejected; got '" + status + "'"));
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed", "limit must be 1-" + MAX_LIMIT);
        }
        List<StaffUpload> found = uploads.list(filter, rows);
        Map<UUID, UserDto> owners = users.findAll(found.stream().map(StaffUpload::ownerId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return found.stream().map(u -> AdminUploadDto.from(u, u.ownerId() == null ? null : owners.get(u.ownerId()))).toList();
    }

    @PostMapping("/content-reviews/{reviewId}")
    @Operation(summary = "Decide a flagged upload (studio or owner, audited as review.decide)", description = "`approved` makes the upload "
            + "`ready`; `rejected` makes it `rejected` and the `note` becomes the customer's `message`. 404 for an unknown review; 409 "
            + "`review_already_decided` when it was decided before.")
    AdminUploadDto decide(@PathVariable UUID reviewId, @Valid @RequestBody DecisionRequest request, StaffPrincipal staff) {
        boolean approve = request.decision() == Decision.approved;
        StaffUpload decided = uploads.decide(reviewId, approve, request.note(), staff.email());
        UploadReview review = decided.review();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", UploadReview.PENDING);
        before.put("upload_id", decided.upload().id());
        before.put("upload_status", UploadStatus.pending_review);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", review == null ? request.decision().name() : review.status());
        after.put("upload_id", decided.upload().id());
        after.put("upload_status", decided.upload().status());
        after.put("note", review == null ? null : review.decisionNote());
        audit.record(staff.email(), AuditLog.REVIEW_DECIDE, reviewId.toString(), before, after);
        return AdminUploadDto.from(decided, decided.ownerId() == null ? null : users.find(decided.ownerId()).orElse(null));
    }

    enum Decision {
        approved, rejected
    }

    /** {@code ContentReviewDecision} in the management contract. */
    record DecisionRequest(
            @NotNull(message = "decision is required (approved or rejected)") Decision decision,
            @Size(max = 200, message = "note must be at most 200 characters") String note) {
    }

    /** {@code AdminUpload}: the storefront {@code Upload} plus owner, origin and the latest review; {@code url} works in any status. */
    record AdminUploadDto(UUID id, UploadKind kind, String format, long bytes, String sha256, UploadStatus status, String url, String message,
            Instant createdAt, Owner owner, String origin, Review review) {

        static AdminUploadDto from(StaffUpload staff, UserDto user) {
            var upload = staff.upload();
            UploadReview r = staff.review();
            return new AdminUploadDto(upload.id(), upload.kind(), upload.format(), upload.bytes(), upload.sha256(), upload.status(), staff.url(),
                    upload.message(), upload.createdAt(), new Owner(staff.ownerId(), staff.guestId(), user == null ? null : user.phone()),
                    upload.origin(), r == null ? null : new Review(r.id(), r.reason(), r.status(), r.decisionNote(), r.reviewerEmail(), r.decidedAt()));
        }
    }

    record Owner(UUID userId, UUID guestId, String phone) {
    }

    record Review(UUID id, String reason, String status, String decisionNote, String reviewerEmail, Instant decidedAt) {
    }
}
