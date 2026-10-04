package studio.aakar.api.media.internal;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

/**
 * {@code aakar.uploads.*}: customer uploads (plan §4).
 *
 * @param scanner {@code terms} (default: flags file names that mention an active content term of the portal or a
 *                {@code flag-terms} entry) or {@code noop} (passes everything; refused by the production guard)
 * @param flagTerms words that send an upload to the review queue when its file name mentions them (case, spaces and
 *                  punctuation ignored, short terms as whole words), on top of the portal's content terms
 *                  ({@code content_terms}, the trademark guardrail of plan §8); empty by default
 * @param maxImageBytes photos, default 15 MB
 * @param maxModelBytes model files, default 50 MB ({@code spring.servlet.multipart.max-file-size} sits just above)
 */
@ConfigurationProperties(prefix = "aakar.uploads")
public record UploadProperties(
        @DefaultValue("terms") String scanner,
        @DefaultValue List<String> flagTerms,
        @DefaultValue("15MB") @DataSizeUnit(DataUnit.BYTES) DataSize maxImageBytes,
        @DefaultValue("50MB") @DataSizeUnit(DataUnit.BYTES) DataSize maxModelBytes) {

    public UploadProperties {
        flagTerms = flagTerms == null ? List.of() : flagTerms.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
    }
}
