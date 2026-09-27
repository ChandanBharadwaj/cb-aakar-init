package studio.aakar.api.studio.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.aakar.api.shared.SseHub;
import studio.aakar.api.studio.JobStageEvent;

/** The job progress SSE hub: {@code event: stage}, id = sequence, closes after {@code ready} or {@code failed}. */
@Configuration
class JobEventStreams {

    @Bean
    SseHub<JobStageEvent> jobEventHub() {
        return new SseHub<>("stage", JobStageEvent::sequence, event -> event.stage().terminal());
    }
}
