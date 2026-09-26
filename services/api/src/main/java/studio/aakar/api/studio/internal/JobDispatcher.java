package studio.aakar.api.studio.internal;

import java.util.UUID;
import studio.aakar.api.studio.DesignGeneratePayload;

/**
 * Hands a {@code design.generate} message to the geometry service. One implementation per profile:
 * {@link DirectJobDispatcher} (HTTP, default) and {@link RabbitJobDispatcher} (AMQP).
 */
interface JobDispatcher {

    void dispatch(UUID jobId, DesignGeneratePayload payload);
}
