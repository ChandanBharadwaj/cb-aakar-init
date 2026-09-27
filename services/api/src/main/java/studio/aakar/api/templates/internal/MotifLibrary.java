package studio.aakar.api.templates.internal;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import studio.aakar.api.shared.AakarProperties;
import studio.aakar.api.templates.MotifDto;
import studio.aakar.api.templates.Motifs;

/**
 * The motif library read the way the geometry service's {@code features/motif.py} reads it: {@code index.json} lists
 * every motif ({@code id}, {@code label}, {@code file}, {@code tags}, {@code min_scale}) and each SVG sits beside it. Loaded
 * once at startup and kept in memory (index and artwork, a few kilobytes). A file name must stay inside the folder: no
 * folders, no {@code ..}, an {@code .svg} name and no symbolic link out; request paths never reach the file system, the
 * artwork is looked up by motif id.
 *
 * <p>A missing or broken library is a deployment fault: with {@code aakar.profile=production} the application refuses to
 * start; locally it logs a warning and the library is empty ({@link #available()} false).
 */
@Component
class MotifLibrary implements Motifs {

    static final String INDEX = "index.json";
    /** Where the library lives in the monorepo, found from the working directory upwards when no folder is configured. */
    static final Path REPO_FOLDER = Paths.get("packages", "design-tokens", "motifs");
    static final String SVG_PATH = "/api/motifs/";
    /** {@code design-spec.v1.json#/$defs/motif}: scale 0.2–1; a motif's {@code min_scale} lies in that range. */
    static final double MIN_SCALE_FLOOR = 0.2;
    static final double MAX_SCALE = 1.0;
    static final long MAX_SVG_BYTES = 512 * 1024;
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern FILE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}\\.svg$");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(MotifLibrary.class);

    private final Path dir;
    private final boolean available;
    private final List<MotifDto> motifs;
    private final Map<String, MotifDto> byId;
    private final Map<String, byte[]> svgs;

    @Autowired
    MotifLibrary(MotifProperties properties, AakarProperties aakar) {
        this(locate(properties.dir()), aakar.api().publicUrl(), aakar.production());
    }

    /**
     * @param dir the library folder, or null when none was found
     * @param publicUrl the API's browser origin, the base of every {@code svg_url}
     * @param production whether a missing or broken library must stop the application
     */
    MotifLibrary(Path dir, String publicUrl, boolean production) {
        Loaded loaded;
        try {
            loaded = load(dir, publicUrl);
            log.info("Motif library (Buti): {} motifs from {}", loaded.motifs().size(), dir);
        } catch (LibraryException e) {
            if (production) {
                throw new IllegalStateException("aakar.profile=production needs the motif library (aakar.motifs.dir, AAKAR_MOTIFS_DIR): "
                        + e.getMessage(), e);
            }
            log.warn("Motif library (Buti) unavailable: {}. GET /api/motifs lists nothing and motif ids and scales on designs are left "
                    + "to the geometry service; point aakar.motifs.dir (AAKAR_MOTIFS_DIR) at packages/design-tokens/motifs", e.getMessage());
            loaded = new Loaded(List.of(), Map.of());
        }
        this.dir = dir;
        this.available = !loaded.motifs().isEmpty();
        this.motifs = loaded.motifs();
        Map<String, MotifDto> index = new LinkedHashMap<>();
        motifs.forEach(m -> index.put(m.id(), m));
        this.byId = Map.copyOf(index);
        this.svgs = loaded.svgs();
    }

    @Override
    public List<MotifDto> list() {
        return motifs;
    }

    @Override
    public Optional<MotifDto> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<byte[]> svg(String id) {
        byte[] svg = id == null ? null : svgs.get(id);
        return svg == null ? Optional.empty() : Optional.of(svg.clone());
    }

    @Override
    public boolean available() {
        return available;
    }

    /** The folder the library was read from (null when none was found). */
    Path dir() {
        return dir;
    }

    /**
     * {@code aakar.motifs.dir} when set, else the first {@code packages/design-tokens/motifs} holding an {@code index.json}
     * from the working directory upwards (running from {@code services/api}, the tests and {@code bootRun} find the monorepo's).
     */
    static Path locate(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        for (Path folder = Paths.get("").toAbsolutePath(); folder != null; folder = folder.getParent()) {
            Path candidate = folder.resolve(REPO_FOLDER);
            if (Files.isRegularFile(candidate.resolve(INDEX))) {
                return candidate.normalize();
            }
        }
        return null;
    }

    // ---- loading ------------------------------------------------------------------------------------------------------

    record Loaded(List<MotifDto> motifs, Map<String, byte[]> svgs) {
    }

    /** Why the library could not be read; always a deployment fault, never the customer's. */
    static final class LibraryException extends RuntimeException {

        LibraryException(String message) {
            super(message);
        }

        LibraryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Loaded load(Path dir, String publicUrl) {
        if (dir == null) {
            throw new LibraryException("no " + REPO_FOLDER.toString().replace('\\', '/') + " above the working directory and no aakar.motifs.dir");
        }
        Path root;
        try {
            root = dir.toRealPath();
        } catch (IOException e) {
            throw new LibraryException("the folder " + dir + " does not exist", e);
        }
        if (!Files.isDirectory(root)) {
            throw new LibraryException(dir + " is not a folder");
        }
        JsonNode index;
        try {
            index = JSON.readTree(Files.readString(root.resolve(INDEX), StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            throw new LibraryException(INDEX + " is missing in " + dir, e);
        } catch (JacksonException e) {
            throw new LibraryException(INDEX + " is not JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new LibraryException(INDEX + " cannot be read: " + e.getMessage(), e);
        }
        JsonNode entries = index == null || !index.isObject() ? null : index.get("motifs");
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            throw new LibraryException(INDEX + " lists no motifs");
        }
        List<MotifDto> motifs = new ArrayList<>();
        Map<String, byte[]> svgs = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            String id = text(entry, "id");
            if (id == null || !ID.matcher(id).matches() || svgs.containsKey(id)) {
                throw new LibraryException("a motif id is missing, malformed or repeated: " + entry);
            }
            JsonNode scale = entry.get("min_scale");
            double minScale = scale == null ? MIN_SCALE_FLOOR : scale.isNumber() ? scale.asDouble() : Double.NaN;
            if (!(minScale >= MIN_SCALE_FLOOR && minScale <= MAX_SCALE)) {
                throw new LibraryException("min_scale of " + id + " must be a number between 0.2 and 1");
            }
            String file = text(entry, "file");
            byte[] svg = readSvg(root, id, file == null ? id + ".svg" : file);
            List<String> tags = new ArrayList<>();
            entry.path("tags").forEach(t -> tags.add(t.asText()));
            String label = text(entry, "label");
            motifs.add(new MotifDto(id, label == null ? defaultLabel(id) : label, tags, minScale, publicUrl + SVG_PATH + id + ".svg"));
            svgs.put(id, svg);
        }
        return new Loaded(List.copyOf(motifs), Map.copyOf(svgs));
    }

    /** A non-blank text member, or null. */
    private static String text(JsonNode entry, String member) {
        JsonNode value = entry.get(member);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /** The artwork of one motif, refused when its file name would leave the folder or it is not an SVG document. */
    private static byte[] readSvg(Path root, String id, String file) {
        if (!FILE.matcher(file).matches() || file.contains("..")) {
            throw new LibraryException("the file of " + id + " must be an .svg name inside the library folder, not " + file);
        }
        Path path = root.resolve(file).normalize();
        try {
            if (!path.getParent().equals(root) || !Files.isRegularFile(path) || !path.toRealPath().getParent().equals(root)) {
                throw new LibraryException("the file of " + id + " (" + file + ") is missing or outside the library folder");
            }
            if (Files.size(path) > MAX_SVG_BYTES) {
                throw new LibraryException("the file of " + id + " (" + file + ") is larger than " + MAX_SVG_BYTES / 1024 + " KB");
            }
            byte[] bytes = Files.readAllBytes(path);
            if (!new String(bytes, StandardCharsets.UTF_8).contains("<svg")) {
                throw new LibraryException("the file of " + id + " (" + file + ") is not an SVG document");
            }
            return bytes;
        } catch (IOException e) {
            throw new LibraryException("the file of " + id + " (" + file + ") cannot be read: " + e.getMessage(), e);
        }
    }

    /** As the geometry service names an unlabelled motif: {@code star_rangoli} → "Star rangoli". */
    private static String defaultLabel(String id) {
        String words = id.replace('_', ' ');
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1).toLowerCase(Locale.ROOT);
    }
}
