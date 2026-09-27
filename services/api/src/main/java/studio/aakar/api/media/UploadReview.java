package studio.aakar.api.media;

import java.time.Instant;
import java.util.UUID;

/**
 * A content review of a flagged upload: why the scanner flagged it ({@code reason}, for staff only) and, once decided,
 * the decision ({@code approved} | {@code rejected}), the reviewer's note (shown to the customer on a rejection) and who
 * decided when. {@code status} is {@code pending} until then.
 */
public record UploadReview(UUID id, UUID uploadId, String reason, String status, String decisionNote, String reviewerEmail, Instant createdAt,
        Instant decidedAt) {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    public boolean pending() {
        return PENDING.equals(status);
    }
}
