package studio.aakar.api.media;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * Where staff uploads live. The local store writes under {@code aakar.media.dir} and serves files at
 * {@code {aakar.api.public-url}/media/{key}}; S3 arrives as another implementation.
 */
public interface MediaStore {

    /**
     * Stores {@code bytes} under {@code folder/<random>.<ext>} and returns where it is served.
     *
     * @param folder one or more path segments of {@code [A-Za-z0-9_-]}, e.g. {@code qc/AK-000001}
     * @param originalFilename used for the extension; the content type decides when it has none
     */
    StoredMedia store(String folder, String originalFilename, byte[] bytes, String contentType);

    /** The stored file for a key, or empty when there is none (or the key tries to leave the directory). */
    Optional<Resource> load(String key);
}
