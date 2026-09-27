package studio.aakar.api.media.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code aakar.media.*}: {@code dir} is the local asset directory (default {@code ./.aakar-media}). */
@ConfigurationProperties(prefix = "aakar.media")
public record MediaProperties(@DefaultValue("./.aakar-media") String dir) {
}
