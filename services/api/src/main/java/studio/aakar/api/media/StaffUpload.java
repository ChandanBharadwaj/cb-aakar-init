package studio.aakar.api.media;

import java.util.UUID;

/**
 * An upload as staff see it in the review queue: the customer view plus a URL that works whatever the status (a
 * reviewer has to look at a file before it is ready), the owning identity and the latest content review, if any.
 */
public record StaffUpload(UploadDto upload, String url, UUID ownerId, UUID guestId, UploadReview review) {
}
