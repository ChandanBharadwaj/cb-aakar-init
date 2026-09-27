package studio.aakar.api.media.internal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import studio.aakar.api.media.ContentScanner;
import studio.aakar.api.media.MediaStore;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.StaffUpload;
import studio.aakar.api.media.StoredMedia;
import studio.aakar.api.media.UploadDto;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.media.UploadStatus;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Customer uploads (plan §4, ADR-0014): format sniffing by extension and magic bytes, size limits per kind, the
 * content scan, storage under {@code uploads/<owner>/<id>.<format>} and the review queue. The owner folder is a
 * one-way digest of the identity, so a guest id (a guest's only credential) never appears in a URL.
 */
@Service
class UploadService implements Uploads {

    static final String UPLOADS_FOLDER = "uploads";
    static final int MAX_LIST = 200;
    static final int REASON_MAX = 200;
    private static final String IMAGE_FORMATS_COPY = "PNG, JPG, WEBP or HEIC";
    private static final String MODEL_FORMATS_COPY = ".stl, .obj, .3mf, .glb, .gltf, .ply or .off";
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadRepository uploads;
    private final ContentReviewRepository reviews;
    private final MediaStore store;
    private final ContentScanner scanner;
    private final UploadProperties properties;
    private final Clock clock;

    UploadService(UploadRepository uploads, ContentReviewRepository reviews, MediaStore store, ContentScanner scanner, UploadProperties properties,
            Clock clock) {
        this.uploads = uploads;
        this.reviews = reviews;
        this.store = store;
        this.scanner = scanner;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UploadDto store(Identity owner, UploadKind kind, MultipartFile file) {
        owner.requireKnown();
        if (kind == null) {
            throw ApiProblemException.validation("kind must be image or model");
        }
        if (file == null || file.isEmpty()) {
            throw ApiProblemException.validation("A non-empty multipart part 'file' is required");
        }
        String filename = file.getOriginalFilename();
        UploadFormats.Format format = UploadFormats.byFilename(filename).filter(f -> f.kind() == kind)
                .orElseThrow(() -> unsupported(kind));
        long limit = limit(kind);
        if (file.getSize() > limit) {
            throw tooLarge(kind, file.getSize(), limit);
        }
        byte[] bytes = read(file);
        if (bytes.length > limit) {
            throw tooLarge(kind, bytes.length, limit);
        }
        if (!format.matches(bytes)) {
            throw ApiProblemException.unprocessable(ProblemCodes.UNSUPPORTED_FORMAT, "Unsupported format",
                    "That file doesn't look like a real " + format.label() + "; it may be damaged or renamed. Export it again and retry.",
                    Map.of("kind", kind.name(), "format", format.id()));
        }
        ScanResult scan = scanner.scan(kind, filename, format.id(), bytes);
        if (scan.isMalware()) {
            log.warn("Refused an upload from {} ({}): {}", owner, format.id(), scan.reason());
            throw ApiProblemException.unprocessable(ProblemCodes.UPLOAD_REJECTED, "Upload rejected",
                    "We can't accept this file. Please try a different one.");
        }

        UUID id = UUID.randomUUID();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // what PostgreSQL keeps, so the 201 equals every later read
        StoredMedia stored = store.storeAs(UPLOADS_FOLDER + "/" + ownerFolder(owner), id.toString(), format.id(), bytes, format.contentType());
        UploadStatus status = scan.isFlagged() ? UploadStatus.pending_review : UploadStatus.ready;
        UploadEntity upload = uploads.save(new UploadEntity(id, owner, kind, format.id(), stored.key(), stored.url(), format.contentType(),
                bytes.length, sha256(bytes), status, now));
        ContentReviewEntity review = null;
        if (scan.isFlagged()) {
            review = reviews.save(new ContentReviewEntity(UUID.randomUUID(), id, truncate(scan.reason(), REASON_MAX), now));
            log.info("Upload {} ({} {}, {} bytes) from {} held for review: {}", id, kind, format.id(), bytes.length, owner, scan.reason());
        } else {
            log.info("Upload {} ({} {}, {} bytes) from {} stored as {}", id, kind, format.id(), bytes.length, owner, stored.key());
        }
        return toDto(upload, review);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UploadDto> find(UUID uploadId) {
        if (uploadId == null) {
            return Optional.empty();
        }
        return uploads.findById(uploadId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UploadDto> findOwned(UUID uploadId, Identity identity) {
        if (uploadId == null) {
            return Optional.empty();
        }
        return uploads.findById(uploadId).filter(u -> u.ownedBy(identity)).map(this::toDto);
    }

    @Override
    @Transactional
    public int attachGuest(UUID guestId, UUID userId) {
        int moved = uploads.attachGuest(guestId, userId);
        if (moved > 0) {
            log.info("{} guest uploads of {} now belong to user {}", moved, guestId, userId);
        }
        return moved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffUpload> list(UploadStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(MAX_LIST, limit)));
        List<UploadEntity> rows = status == null ? uploads.findAllByOrderByCreatedAtDesc(page) : uploads.findByStatusOrderByCreatedAtDesc(status, page);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, ContentReviewEntity> latest = new LinkedHashMap<>();
        reviews.findByUploadIdInOrderByCreatedAtAsc(rows.stream().map(UploadEntity::id).toList()).forEach(r -> latest.put(r.uploadId(), r));
        return rows.stream().map(u -> staff(u, latest.get(u.id()))).toList();
    }

    @Override
    @Transactional
    public StaffUpload decide(UUID reviewId, boolean approve, String note, String reviewerEmail) {
        ContentReviewEntity review = reviews.lockById(reviewId).orElseThrow(() -> ApiProblemException.notFound("Content review", reviewId));
        if (!review.pending()) {
            throw ApiProblemException.conflict(ProblemCodes.REVIEW_ALREADY_DECIDED, "Review already decided",
                    "Content review " + reviewId + " was already " + review.status() + "; a decision is final");
        }
        UploadEntity upload = uploads.findById(review.uploadId())
                .orElseThrow(() -> new IllegalStateException("Content review " + reviewId + " refers to missing upload " + review.uploadId()));
        String cleanNote = note == null || note.isBlank() ? null : truncate(note.trim(), REASON_MAX);
        review.decide(approve, cleanNote, reviewerEmail, clock.instant().truncatedTo(ChronoUnit.MICROS));
        upload.markStatus(approve ? UploadStatus.ready : UploadStatus.rejected);
        log.info("Content review {} of upload {}: {} by {}", reviewId, upload.id(), review.status(), reviewerEmail);
        return staff(upload, review);
    }

    // ---- mapping ------------------------------------------------------------------------------------------------------

    private UploadDto toDto(UploadEntity upload) {
        ContentReviewEntity review = upload.status() == UploadStatus.rejected
                ? reviews.findFirstByUploadIdOrderByCreatedAtDesc(upload.id()).orElse(null) : null;
        return toDto(upload, review);
    }

    /** The customer view: the browser URL only once the file is ready, the reviewer's note once it is rejected. */
    private UploadDto toDto(UploadEntity upload, ContentReviewEntity latestReview) {
        String message = upload.status() == UploadStatus.rejected && latestReview != null ? latestReview.decisionNote() : null;
        return new UploadDto(upload.id(), upload.kind(), upload.format(), upload.bytes(), upload.sha256(), upload.status(),
                upload.status() == UploadStatus.ready ? upload.url() : null, message, upload.createdAt(), store.internalUrl(upload.storageKey()),
                upload.origin(), upload.provider());
    }

    private StaffUpload staff(UploadEntity upload, ContentReviewEntity latestReview) {
        return new StaffUpload(toDto(upload, latestReview), upload.url(), upload.ownerId(), upload.guestId(),
                latestReview == null ? null : latestReview.toDto());
    }

    // ---- rules --------------------------------------------------------------------------------------------------------

    long limit(UploadKind kind) {
        return (kind == UploadKind.image ? properties.maxImageBytes() : properties.maxModelBytes()).toBytes();
    }

    private static ApiProblemException unsupported(UploadKind kind) {
        String detail = kind == UploadKind.image
                ? "That isn't a photo we can use. Upload a " + IMAGE_FORMATS_COPY + " image."
                : "That isn't a model file we can print. Save it from your 3D program as " + MODEL_FORMATS_COPY + ".";
        return ApiProblemException.unprocessable(ProblemCodes.UNSUPPORTED_FORMAT, "Unsupported format", detail,
                Map.of("kind", kind.name(), "formats", UploadFormats.ids(kind)));
    }

    private static ApiProblemException tooLarge(UploadKind kind, long bytes, long limit) {
        String what = kind == UploadKind.image ? "Photos" : "Model files";
        return new ApiProblemException(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCodes.PAYLOAD_TOO_LARGE, "File too large",
                what + " can be up to " + megabytes(limit, RoundingMode.HALF_UP) + "; this one is " + megabytes(bytes, RoundingMode.CEILING) + ".",
                Map.of("max_bytes", limit, "bytes", bytes));
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiProblemException.validation("The uploaded file could not be read");
        }
    }

    /**
     * {@code 15728640} → {@code "15 MB"}, {@code 16252928} → {@code "15.5 MB"} (binary megabytes, like the browser); a file's
     * size is rounded up so one byte over the limit never reads as the limit itself.
     */
    static String megabytes(long bytes, RoundingMode rounding) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024L * 1024L), 1, rounding).stripTrailingZeros().toPlainString() + " MB";
    }

    /** A stable, non-reversible folder per owning identity: the first 32 hex digits of SHA-256 over kind and id. */
    static String ownerFolder(Identity owner) {
        return sha256(("aakar-upload-owner:" + owner.kind() + ":" + owner.id()).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
