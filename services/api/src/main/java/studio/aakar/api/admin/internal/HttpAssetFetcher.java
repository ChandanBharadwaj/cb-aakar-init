package studio.aakar.api.admin.internal;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import studio.aakar.api.shared.RestClients;

/** Fetches asset URLs (geometry service static files or a bucket) with a generous read timeout. */
@Component
class HttpAssetFetcher implements AssetFetcher {

    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(HttpAssetFetcher.class);

    private final RestClient http;

    HttpAssetFetcher(RestClients clients) {
        this.http = clients.raw(READ_TIMEOUT);
    }

    @Override
    public Optional<byte[]> fetch(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = http.get().uri(url).retrieve().body(byte[].class);
            return bytes == null ? Optional.empty() : Optional.of(bytes);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Print pack: cannot fetch {}: {}", url, e.toString());
            return Optional.empty();
        }
    }
}
