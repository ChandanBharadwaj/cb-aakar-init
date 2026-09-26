package studio.aakar.api.design.internal;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.studio.GenerationCompleted;
import studio.aakar.api.studio.GenerationFailed;

/**
 * Applies job outcomes to versions. Runs synchronously inside the studio's transaction, so a version
 * is {@code ready} before any client is told so.
 */
@Component
class GenerationResultListener {

    private static final Logger log = LoggerFactory.getLogger(GenerationResultListener.class);

    private final DesignVersionRepository versions;
    private final DesignRepository designs;

    GenerationResultListener(DesignVersionRepository versions, DesignRepository designs) {
        this.versions = versions;
        this.designs = designs;
    }

    @EventListener
    @Transactional
    public void on(GenerationCompleted event) {
        versions.findById(event.versionId()).ifPresentOrElse(version -> {
            version.markReady(event.payload());
            designs.findById(version.designId()).ifPresent(d -> d.touch(Instant.now()));
        }, () -> log.warn("Completed job {} refers to unknown version {}", event.jobId(), event.versionId()));
    }

    @EventListener
    @Transactional
    public void on(GenerationFailed event) {
        versions.findById(event.versionId()).ifPresentOrElse(version -> {
            version.markFailed();
            designs.findById(version.designId()).ifPresent(d -> d.touch(Instant.now()));
        }, () -> log.warn("Failed job {} refers to unknown version {}", event.jobId(), event.versionId()));
    }
}
