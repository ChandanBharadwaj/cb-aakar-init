package studio.aakar.api.media;

/** A file kept by the {@link MediaStore}: its key, the public URL it is served at, type and size. */
public record StoredMedia(String key, String url, String contentType, long bytes) {
}
