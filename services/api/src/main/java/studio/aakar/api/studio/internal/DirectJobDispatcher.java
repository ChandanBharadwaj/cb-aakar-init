package studio.aakar.api.studio.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import studio.aakar.api.shared.AakarProperties;
import studio.aakar.api.shared.RestClients;
import studio.aakar.api.studio.DesignCompletedPayload;
import studio.aakar.api.studio.DesignFailedPayload;
import studio.aakar.api.studio.DesignGeneratePayload;

/**
 * Default profile: POSTs {@code design.generate} to {@code {aakar.geometry.url}/v1/build} on a dedicated
 * executor and applies the synchronous answer. 200 → {@code design.completed}; 422/500 →
 * {@code design.failed}; anything else or a connection problem → {@code build_error}. Progress arrives
 * meanwhile through {@code POST /internal/jobs/{jobId}/callback}.
 */
@Component
@Profile("direct")
class DirectJobDispatcher implements JobDispatcher {

    static final Duration BUILD_READ_TIMEOUT = Duration.ofSeconds(75);
    private static final Logger log = LoggerFactory.getLogger(DirectJobDispatcher.class);

    private final RestClient http;
    private final ObjectMapper json;
    private final GenerationResultApplier applier;
    private final ThreadPoolTaskExecutor executor;

    DirectJobDispatcher(RestClients clients, AakarProperties properties, ObjectMapper json, GenerationResultApplier applier) {
        this.http = clients.json(properties.geometry().url(), BUILD_READ_TIMEOUT);
        this.json = json;
        this.applier = applier;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("geometry-build-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        log.info("Direct job dispatcher → {}", properties.geometry().url());
    }

    @Override
    public void dispatch(UUID jobId, DesignGeneratePayload payload) {
        executor.execute(() -> build(jobId, payload));
    }

    void build(UUID jobId, DesignGeneratePayload payload) {
        applier.markRunning(jobId);
        RawResponse response;
        try {
            response = http.post().uri("/v1/build")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, res) -> new RawResponse(res.getStatusCode(), res.getBody().readAllBytes()));
        } catch (ResourceAccessException e) {
            log.warn("Geometry service unreachable for job {}: {}", jobId, e.getMessage());
            fail(jobId, payload, DesignFailedPayload.BUILD_ERROR, "We could not reach the studio's geometry service. Please try again in a moment.");
            return;
        } catch (RuntimeException e) {
            log.error("Build request for job {} failed", jobId, e);
            fail(jobId, payload, DesignFailedPayload.BUILD_ERROR, "Something went wrong while building this design.");
            return;
        }
        handle(jobId, payload, response);
    }

    private void handle(UUID jobId, DesignGeneratePayload payload, RawResponse response) {
        try {
            if (response.status().is2xxSuccessful()) {
                DesignCompletedPayload completed = json.readValue(response.body(), DesignCompletedPayload.class);
                applier.applyCompleted(jobId, completed, null);
            } else if (response.status().value() == 422 || response.status().is5xxServerError()) {
                DesignFailedPayload failed = parseFailure(response.body());
                applier.applyFailed(jobId, failed != null ? failed : new DesignFailedPayload(jobId, payload.designId(),
                        DesignFailedPayload.BUILD_ERROR, "The geometry service could not build this design.", null), null);
            } else {
                fail(jobId, payload, DesignFailedPayload.BUILD_ERROR,
                        "The geometry service answered with HTTP " + response.status().value() + ".");
            }
        } catch (IOException e) {
            log.error("Unreadable build response for job {}", jobId, e);
            fail(jobId, payload, DesignFailedPayload.BUILD_ERROR, "The geometry service answered with something we could not read.");
        } catch (RuntimeException e) {
            log.error("Applying the build result for job {} failed", jobId, e);
            fail(jobId, payload, DesignFailedPayload.BUILD_ERROR, "Something went wrong while saving this design.");
        }
    }

    private DesignFailedPayload parseFailure(byte[] body) {
        try {
            DesignFailedPayload failed = json.readValue(body, DesignFailedPayload.class);
            return failed.code() == null ? null : failed;
        } catch (IOException e) {
            return null;
        }
    }

    private void fail(UUID jobId, DesignGeneratePayload payload, String code, String message) {
        try {
            applier.applyFailed(jobId, new DesignFailedPayload(jobId, payload.designId(), code, message, null), null);
        } catch (RuntimeException e) {
            log.error("Could not record failure for job {}", jobId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private record RawResponse(HttpStatusCode status, byte[] body) {
    }
}
