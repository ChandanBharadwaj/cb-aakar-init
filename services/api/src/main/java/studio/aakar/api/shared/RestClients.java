package studio.aakar.api.shared;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds {@link RestClient}s with explicit connect/read timeouts. The geometry service can take up
 * to a minute to build a design, so callers pick a read timeout that fits the call.
 */
@Component
public class RestClients {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient.Builder builder;

    public RestClients(RestClient.Builder builder) {
        this.builder = builder;
    }

    public RestClient json(String baseUrl, Duration readTimeout) {
        return json(baseUrl, DEFAULT_CONNECT_TIMEOUT, readTimeout);
    }

    public RestClient json(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        // HTTP/1.1 only: the geometry service (uvicorn) speaks 1.1 and h2c upgrades confuse some stubs/proxies.
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(http -> http.version(HttpClient.Version.HTTP_1_1))
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(readTimeout));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
