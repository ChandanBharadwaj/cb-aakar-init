package studio.aakar.api.shared;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for the Next.js storefront ({@code http://localhost:3000}) and the management portal
 * ({@code http://localhost:3100}); extend via {@code aakar.cors.origins}. The source is consumed by both Spring
 * Security filter chains, so pre-flight requests are answered before authentication; {@code Authorization} and
 * {@code X-Aakar-Guest} are explicitly allowed request headers and {@code Content-Disposition} is exposed so the
 * portal can read download file names.
 */
@Configuration
public class WebConfig {

    static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:3000", "http://localhost:3100");

    /**
     * {@code aakar.cors.origins} (comma-separated or a list); the older {@code aakar.cors.allowed-origins} is merged in.
     */
    @ConfigurationProperties(prefix = "aakar.cors")
    public record CorsProperties(List<String> origins, List<String> allowedOrigins) {
        public CorsProperties {
            Set<String> merged = new LinkedHashSet<>();
            if (origins != null) {
                merged.addAll(origins);
            }
            if (allowedOrigins != null) {
                merged.addAll(allowedOrigins);
            }
            merged.removeIf(o -> o == null || o.isBlank());
            List<String> all = merged.isEmpty() ? DEFAULT_ORIGINS : List.copyOf(merged);
            origins = all;
            allowedOrigins = all;
        }
    }

    /** Typed as the concrete class: Spring MVC's {@code HandlerMappingIntrospector} is a {@code CorsConfigurationSource} too. */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(new ArrayList<>(cors.origins()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, Identity.GUEST_HEADER, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, HttpHeaders.CACHE_CONTROL, "Last-Event-ID", "X-Requested-With"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION, "Last-Event-ID"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/admin/api/**", config);
        source.registerCorsConfiguration("/media/**", config);
        return source;
    }
}
