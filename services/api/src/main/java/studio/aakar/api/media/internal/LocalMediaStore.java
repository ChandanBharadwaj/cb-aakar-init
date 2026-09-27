package studio.aakar.api.media.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import studio.aakar.api.media.MediaStore;
import studio.aakar.api.media.StoredMedia;
import studio.aakar.api.shared.AakarProperties;

/**
 * Files under {@code aakar.media.dir}, served by {@link MediaController} at {@code /media/{key}}: the public URL is
 * {@code {aakar.api.public-url}/media/{key}}, the internal one (for the geometry service)
 * {@code {aakar.media.internal-base-url}/media/{key}}.
 */
@Component
class LocalMediaStore implements MediaStore {

    static final String PUBLIC_PATH = "/media/";
    private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,8}$");
    private static final Logger log = LoggerFactory.getLogger(LocalMediaStore.class);

    private final Path root;
    private final String publicUrl;
    private final String internalBaseUrl;

    LocalMediaStore(MediaProperties properties, AakarProperties aakar) {
        this.root = Paths.get(properties.dir()).toAbsolutePath().normalize();
        this.publicUrl = aakar.api().publicUrl();
        String internal = properties.internalBaseUrl();
        this.internalBaseUrl = internal == null || internal.isBlank() ? publicUrl : stripSlash(internal.trim());
    }

    @Override
    public StoredMedia store(String folder, String originalFilename, byte[] bytes, String contentType) {
        return storeAs(folder, UUID.randomUUID().toString(), extension(originalFilename, contentType), bytes, contentType);
    }

    @Override
    public StoredMedia storeAs(String folder, String name, String extension, byte[] bytes, String contentType) {
        if (name == null || !SEGMENT.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid media file name '" + name + "'");
        }
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid media file extension '" + extension + "'");
        }
        String key = folderKey(folder) + "/" + name + "." + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Media key " + key + " leaves the media directory");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write media file " + target, e);
        }
        log.info("Stored {} ({} bytes, {}) under {}", key, bytes.length, contentType, root);
        return new StoredMedia(key, publicUrl + PUBLIC_PATH + key, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType,
                bytes.length);
    }

    @Override
    public Optional<Resource> load(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/") || key.contains("\\")) {
            return Optional.empty();
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(path));
    }

    @Override
    public String internalUrl(String key) {
        return internalBaseUrl + PUBLIC_PATH + key;
    }

    private static String folderKey(String folder) {
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("folder is required");
        }
        for (String segment : folder.split("/")) {
            if (!SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("Invalid media folder segment '" + segment + "'");
            }
        }
        return folder;
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static String extension(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (EXTENSION.matcher(ext).matches()) {
                    return ext;
                }
            }
        }
        if (contentType != null) {
            String type = contentType.toLowerCase(Locale.ROOT);
            if (type.startsWith("image/jpeg")) {
                return "jpg";
            }
            if (type.startsWith("image/png")) {
                return "png";
            }
            if (type.startsWith("image/webp")) {
                return "webp";
            }
            if (type.startsWith("image/heic")) {
                return "heic";
            }
            if (type.startsWith("application/pdf")) {
                return "pdf";
            }
        }
        return "bin";
    }
}
