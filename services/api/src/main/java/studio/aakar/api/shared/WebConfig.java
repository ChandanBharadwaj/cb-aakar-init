package studio.aakar.api.shared;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for the Next.js storefront (dev origin {@code http://localhost:3000}, extend via
 * {@code aakar.cors.allowed-origins}). The source is consumed by the Spring Security filter chain, so
 * pre-flight requests are answered before authentication; {@code Authorization} and {@code X-Aakar-Guest}
 * are explicitly allowed request headers.
 */
@Configuration
public class WebConfig {

    @ConfigurationProperties(prefix = "aakar.cors")
    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("http://localhost:3000") : List.copyOf(allowedOrigins);
        }
    }

    /** Typed as the concrete class: Spring MVC's {@code HandlerMappingIntrospector} is a {@code CorsConfigurationSource} too. */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, Identity.GUEST_HEADER, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, HttpHeaders.CACHE_CONTROL, "Last-Event-ID", "X-Requested-With"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION, "Last-Event-ID"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
