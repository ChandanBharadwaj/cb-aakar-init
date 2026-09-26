package studio.aakar.api.studio.internal;

import java.util.UUID;
import studio.aakar.api.studio.DesignGeneratePayload;

/** Internal event: a job row was written; dispatch it once the transaction commits. */
record JobQueued(UUID jobId, Long outboxId, DesignGeneratePayload payload) {
}
