package studio.aakar.api.studio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code events/envelope.v1.json}: every message on the {@code aakar.design} exchange and every
 * direct-profile callback. The routing key equals {@code type}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope(
        String type,
        int version,
        UUID eventId,
        UUID jobId,
        UUID designId,
        Instant occurredAt,
        Integer sequence,
        Map<String, Object> payload) {

    public static final String DESIGN_GENERATE = "design.generate";
    public static final String DESIGN_PROGRESS = "design.progress";
    public static final String DESIGN_COMPLETED = "design.completed";
    public static final String DESIGN_FAILED = "design.failed";
    public static final int CURRENT_VERSION = 1;

    public static Envelope of(String type, UUID jobId, UUID designId, Integer sequence, Map<String, Object> payload) {
        return new Envelope(type, CURRENT_VERSION, UUID.randomUUID(), jobId, designId, Instant.now(), sequence, payload);
    }
}
