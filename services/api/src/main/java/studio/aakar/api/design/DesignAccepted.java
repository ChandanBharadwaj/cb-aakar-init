package studio.aakar.api.design;

import java.util.UUID;

/** 202 body: the design, the version number just created and the job to follow. */
public record DesignAccepted(UUID designId, int versionNo, UUID jobId, String eventsUrl) {
}
