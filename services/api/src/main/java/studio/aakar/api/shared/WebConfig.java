package studio.aakar.api.shared;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the Next.js storefront (dev origin {@code http://localhost:3000}, extend via {@code aakar.cors.allowed-origins}). */
@Configuration
public class WebConfig {

    @ConfigurationProperties(prefix = "aakar.cors")
    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("http://localhost:3000") : List.copyOf(allowedOrigins);
        }
    }

    @Bean
    WebMvcConfigurer corsConfigurer(CorsProperties cors) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders(HttpHeaders.LOCATION, "Last-Event-ID")
                        .maxAge(3600);
            }
        };
    }
}
