package studio.aakar.api.media;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * Where staff uploads (QC photos) and customer uploads live. The local store writes under {@code aakar.media.dir}
 * and serves files at {@code {aakar.api.public-url}/media/{key}}; S3 arrives as another implementation.
 */
public interface MediaStore {

    /**
     * Stores {@code bytes} under {@code folder/<random>.<ext>} and returns where it is served.
     *
     * @param folder one or more path segments of {@code [A-Za-z0-9_-]}, e.g. {@code qc/AK-000001}
     * @param originalFilename used for the extension; the content type decides when it has none
     */
    StoredMedia store(String folder, String originalFilename, byte[] bytes, String contentType);

    /**
     * Stores {@code bytes} at exactly {@code folder/name.extension}, for callers that own the id (customer uploads are
     * kept as {@code uploads/<owner>/<upload id>.<format>}).
     *
     * @param name one segment of {@code [A-Za-z0-9_-]}
     * @param extension lowercase letters and digits, e.g. {@code png} or {@code 3mf}
     */
    StoredMedia storeAs(String folder, String name, String extension, byte[] bytes, String contentType);

    /** The stored file for a key, or empty when there is none (or the key tries to leave the directory). */
    Optional<Resource> load(String key);

    /**
     * Where another service of the studio (the geometry service) fetches a stored file:
     * {@code {aakar.media.internal-base-url}/media/{key}}, which defaults to the public URL (plan open decision 9).
     */
    String internalUrl(String key);
}
