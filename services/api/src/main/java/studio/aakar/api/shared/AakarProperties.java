package studio.aakar.api.shared;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.*} settings shared across modules.
 *
 * @param profile {@code local} (default) or {@code production}; {@link ProductionGuard} refuses mock adapters in production
 * @param geometry where the geometry service lives (templates and, in the direct profile, builds)
 * @param api how this API is reachable from the geometry service (callback URLs)
 * @param web where the storefront lives (mock pay page URLs)
 */
@Validated
@ConfigurationProperties(prefix = "aakar")
public record AakarProperties(@DefaultValue("local") String profile, Geometry geometry, Api api, @DefaultValue Web web) {

    public static final String PRODUCTION = "production";

    public boolean production() {
        return PRODUCTION.equalsIgnoreCase(profile);
    }

    public record Geometry(@NotBlank String url) {
        public String url() {
            return stripSlash(url);
        }
    }

    public record Api(@NotBlank String publicUrl) {
        public String publicUrl() {
            return stripSlash(publicUrl);
        }
    }

    public record Web(@DefaultValue("http://localhost:3000") String url) {
        public String url() {
            return stripSlash(url);
        }
    }

    private static String stripSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
