package studio.aakar.api.shared;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.*} settings shared across modules.
 *
 * @param geometry where the geometry service lives (templates and, in the direct profile, builds)
 * @param api how this API is reachable from the geometry service (callback URLs)
 */
@Validated
@ConfigurationProperties(prefix = "aakar")
public record AakarProperties(Geometry geometry, Api api) {

    public record Geometry(@NotBlank String url) {
        public String url() {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public record Api(@NotBlank String publicUrl) {
        public String publicUrl() {
            return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        }
    }
}
