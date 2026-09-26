package studio.aakar.api.studio.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import studio.aakar.api.studio.DesignCompletedPayload;
import studio.aakar.api.studio.DesignFailedPayload;
import studio.aakar.api.studio.DesignGeneratePayload;
import studio.aakar.api.studio.DesignProgressPayload;
import studio.aakar.api.studio.Envelope;
import studio.aakar.api.studio.GenerationRequest;

/** Builds and parses the event envelopes and payloads defined in {@code packages/contracts/schemas/events}. */
@Component
public class EnvelopeMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final ObjectMapper json;

    public EnvelopeMapper(ObjectMapper json) {
        this.json = json;
    }

    public DesignGeneratePayload generatePayload(UUID jobId, GenerationRequest request, String callbackUrl) {
        return new DesignGeneratePayload(jobId, request.designId(), request.versionNo(), request.parentVersionId(),
                request.spec(), DesignGeneratePayload.DEFAULT_OUTPUTS, callbackUrl);
    }

    public Envelope generateEnvelope(DesignGeneratePayload payload) {
        return Envelope.of(Envelope.DESIGN_GENERATE, payload.jobId(), payload.designId(), null, toMap(payload));
    }

    public Map<String, Object> toMap(Object payload) {
        return json.convertValue(payload, MAP);
    }

    public DesignProgressPayload progress(Envelope envelope) {
        return json.convertValue(envelope.payload(), DesignProgressPayload.class);
    }

    public DesignCompletedPayload completed(Envelope envelope) {
        return json.convertValue(envelope.payload(), DesignCompletedPayload.class);
    }

    public DesignFailedPayload failed(Envelope envelope) {
        return json.convertValue(envelope.payload(), DesignFailedPayload.class);
    }

    /** Routing key on the {@code aakar.design} topic exchange: identical to the envelope type. */
    public static String routingKey(Envelope envelope) {
        return envelope.type();
    }
}
