package studio.aakar.api.media;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code Upload} in the storefront contract. {@code url} is the browser URL and is only given once the file is
 * {@code ready}; {@code message} carries the reviewer's note when the file was {@code rejected}.
 *
 * <p>The last three components are for other modules, never serialised: {@code internalUrl} is where the geometry
 * service fetches the file ({@code aakar.media.internal-base-url}, see {@code content_source.url} in
 * {@code design-spec.v1.json}), {@code origin} is {@code upload} or {@code generated} and {@code provider} names the
 * generative provider of a generated file.
 */
public record UploadDto(
        UUID id,
        UploadKind kind,
        String format,
        long bytes,
        String sha256,
        UploadStatus status,
        String url,
        String message,
        Instant createdAt,
        @JsonIgnore String internalUrl,
        @JsonIgnore String origin,
        @JsonIgnore String provider) {

    public static final String ORIGIN_UPLOAD = "upload";
    public static final String ORIGIN_GENERATED = "generated";

    @JsonIgnore
    public boolean isReady() {
        return status == UploadStatus.ready;
    }
}
