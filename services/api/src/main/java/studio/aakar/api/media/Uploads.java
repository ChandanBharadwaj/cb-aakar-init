package studio.aakar.api.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import studio.aakar.api.shared.Identity;

/**
 * Public API of customer uploads (photos for a relief, model files for a hero form or Swaroop): stored in the
 * {@link MediaStore} under {@code uploads/<owner>/<id>.<ext>}, owned like designs and carts by the identity that sent
 * them, passed through the {@link ContentScanner}; flagged files wait for a content review in the portal.
 */
public interface Uploads {

    /**
     * Sniffs the format (extension and magic bytes), enforces the size limits, scans and stores the file.
     * 401 {@code unauthenticated} without a user or guest identity; 400 {@code validation_failed} for an empty file;
     * 413 {@code payload_too_large} above {@code aakar.uploads.max-image-bytes} / {@code max-model-bytes};
     * 422 {@code unsupported_format} for a file that is not one of the kind's formats; 422 {@code upload_rejected}
     * when the scanner reports malware.
     */
    UploadDto store(Identity owner, UploadKind kind, MultipartFile file);

    /** Any upload by id, whoever owns it (for a design that already carries it). */
    Optional<UploadDto> find(UUID uploadId);

    /** The upload when {@code identity} (user or guest) owns it; empty for anyone else, including anonymous callers. */
    Optional<UploadDto> findOwned(UUID uploadId, Identity identity);

    /**
     * Moves every upload the guest sent to the user (sign-in hand-over, beside the designs and the cart).
     *
     * @return how many uploads changed owner
     */
    int attachGuest(UUID guestId, UUID userId);

    /** Newest first, optionally only one status, at most {@code limit} rows (management API review queue). */
    List<StaffUpload> list(UploadStatus status, int limit);

    /**
     * Decides a pending content review: {@code approve} makes the upload {@code ready}, otherwise it becomes
     * {@code rejected} and {@code note} is what the customer reads. 404 for an unknown review; 409
     * {@code review_already_decided} when it was decided before.
     */
    StaffUpload decide(UUID reviewId, boolean approve, String note, String reviewerEmail);
}
