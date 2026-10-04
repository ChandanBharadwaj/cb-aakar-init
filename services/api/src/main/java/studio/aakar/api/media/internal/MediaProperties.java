package studio.aakar.api.media.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code aakar.media.*}: {@code dir} is the local asset directory (default {@code ./.aakar-media});
 * {@code internal-base-url} is the API origin the geometry service reaches media at (e.g. {@code http://api:8080} in
 * Compose), used for {@code content_source.url} in design specs; blank means the public URL ({@code aakar.api.public-url}).
 */
@ConfigurationProperties(prefix = "aakar.media")
public record MediaProperties(@DefaultValue("./.aakar-media") String dir, String internalBaseUrl) {
}
