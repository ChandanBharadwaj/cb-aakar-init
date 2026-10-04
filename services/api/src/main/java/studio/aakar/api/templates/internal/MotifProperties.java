package studio.aakar.api.templates.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code aakar.motifs.*}: {@code dir} is the motif library folder ({@code index.json} and the SVGs beside it; env
 * {@code AAKAR_MOTIFS_DIR}, {@code /design-tokens/motifs} in the image). Blank means the first
 * {@code packages/design-tokens/motifs} found from the working directory upwards, i.e. the monorepo's own folder.
 */
@ConfigurationProperties(prefix = "aakar.motifs")
public record MotifProperties(String dir) {
}
