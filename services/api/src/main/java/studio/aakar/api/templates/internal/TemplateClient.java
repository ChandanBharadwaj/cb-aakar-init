package studio.aakar.api.templates.internal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import studio.aakar.api.shared.AakarProperties;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.RestClients;
import studio.aakar.api.templates.TemplateDescriptor;

/** Fetches {@code GET {aakar.geometry.url}/v1/templates} and caches the list for 60 seconds. */
@Component
class TemplateClient {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(TemplateClient.class);
    private static final String ALL = "all";

    private final RestClient http;
    private final LoadingCache<String, List<TemplateDescriptor>> cache;

    TemplateClient(RestClients clients, AakarProperties properties) {
        this.http = clients.json(properties.geometry().url(), Duration.ofSeconds(10));
        this.cache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build(key -> fetch());
    }

    List<TemplateDescriptor> all() {
        return cache.get(ALL);
    }

    void invalidate() {
        cache.invalidateAll();
    }

    private List<TemplateDescriptor> fetch() {
        try {
            List<TemplateDescriptor> descriptors = http.get().uri("/v1/templates").retrieve()
                    .body(new ParameterizedTypeReference<List<TemplateDescriptor>>() { });
            log.debug("Loaded {} template descriptors from the geometry service", descriptors == null ? 0 : descriptors.size());
            return descriptors == null ? List.of() : descriptors;
        } catch (ResourceAccessException e) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.GEOMETRY_UNAVAILABLE,
                    "Geometry service unavailable", "The geometry service could not be reached: " + e.getMessage());
        } catch (RestClientException e) {
            throw new ApiProblemException(HttpStatus.BAD_GATEWAY, ProblemCodes.GEOMETRY_UNAVAILABLE,
                    "Geometry service error", "The geometry service answered unexpectedly: " + e.getMessage());
        }
    }
}
