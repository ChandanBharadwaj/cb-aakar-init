package studio.aakar.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;

/**
 * Access to {@code packages/contracts} and {@code packages/design-tokens} from the API's test working
 * directory ({@code services/api}). Override the monorepo root with {@code -Daakar.repo.root=…} or
 * {@code AAKAR_REPO_ROOT}.
 */
public final class Contracts {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Contracts() {
    }

    public static Path repoRoot() {
        String override = System.getProperty("aakar.repo.root", System.getenv("AAKAR_REPO_ROOT"));
        return override != null ? Paths.get(override) : Paths.get("..", "..").toAbsolutePath().normalize();
    }

    public static Path contracts(String relative) {
        return repoRoot().resolve("packages/contracts").resolve(relative);
    }

    public static Path designTokens(String relative) {
        return repoRoot().resolve("packages/design-tokens").resolve(relative);
    }

    /** Aborts the calling test with a clear message when the monorepo file is missing. */
    public static Path require(Path path) {
        Assumptions.assumeTrue(Files.exists(path), () -> "Skipping: " + path + " not found (run from the monorepo or set -Daakar.repo.root)");
        return path;
    }

    public static JsonNode readJson(Path path) {
        try {
            return JSON.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Validates {@code document} against a self-contained draft 2020-12 schema file. */
    public static Set<ValidationMessage> validate(Path schemaFile, JsonNode document) {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(readJson(schemaFile));
        return schema.validate(document);
    }
}
