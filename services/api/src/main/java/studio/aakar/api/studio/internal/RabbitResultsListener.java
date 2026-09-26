package studio.aakar.api.studio.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.studio.Envelope;

/** Profile {@code rabbit}: consumes result envelopes and applies them (idempotently) to the job. */
@Component
@Profile("rabbit")
class RabbitResultsListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitResultsListener.class);

    private final GenerationResultApplier applier;

    RabbitResultsListener(GenerationResultApplier applier) {
        this.applier = applier;
    }

    @RabbitListener(queues = RabbitConfig.RESULTS_QUEUE)
    public void onResult(Envelope envelope) {
        try {
            applier.apply(envelope);
        } catch (ApiProblemException e) {
            // Unknown job or malformed payload: log and acknowledge; redelivery would not help.
            log.warn("Dropping {} for job {}: {}", envelope.type(), envelope.jobId(), e.getMessage());
        }
    }
}
