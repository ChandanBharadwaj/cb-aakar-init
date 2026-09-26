package studio.aakar.api.studio.internal;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import studio.aakar.api.studio.DesignGeneratePayload;
import studio.aakar.api.studio.Envelope;

/** Profile {@code rabbit}: publishes the {@code design.generate} envelope; results come back via {@link RabbitResultsListener}. */
@Component
@Profile("rabbit")
class RabbitJobDispatcher implements JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RabbitJobDispatcher.class);

    private final RabbitTemplate rabbit;
    private final EnvelopeMapper mapper;

    RabbitJobDispatcher(RabbitTemplate rabbit, EnvelopeMapper mapper) {
        this.rabbit = rabbit;
        this.mapper = mapper;
    }

    @Override
    public void dispatch(UUID jobId, DesignGeneratePayload payload) {
        Envelope envelope = mapper.generateEnvelope(withoutCallback(payload));
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, EnvelopeMapper.routingKey(envelope), envelope);
        log.info("Published {} for job {}", envelope.type(), jobId);
    }

    /** Over AMQP progress comes back on the results queue, so the callback URL is dropped (per the schema). */
    static DesignGeneratePayload withoutCallback(DesignGeneratePayload payload) {
        return new DesignGeneratePayload(payload.jobId(), payload.designId(), payload.versionNo(), payload.parentVersionId(),
                payload.spec(), payload.outputs(), null);
    }
}
