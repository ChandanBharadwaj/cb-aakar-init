package studio.aakar.api.studio.internal;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import studio.aakar.api.studio.JobStageEvent;

/**
 * In-memory registry of SSE subscribers per job. A subscription buffers live events until the
 * controller has replayed the persisted history, then flushes in sequence order; anything at or
 * below the last sent sequence is dropped, so replay and live delivery never duplicate.
 * A {@code : keep-alive} comment goes out every 15 s; the stream completes after {@code ready} or {@code failed}.
 */
@Component
class JobEventStream {

    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(10);
    static final long KEEP_ALIVE_MS = 15_000;
    private static final Logger log = LoggerFactory.getLogger(JobEventStream.class);

    private final Map<UUID, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

    Subscription subscribe(UUID jobId, int lastSeenSequence) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        Subscription subscription = new Subscription(jobId, emitter, lastSeenSequence);
        subscriptions.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>()).add(subscription);
        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> {
            remove(subscription);
            emitter.complete();
        });
        emitter.onError(e -> remove(subscription));
        return subscription;
    }

    void publish(JobStageEvent event) {
        List<Subscription> subs = subscriptions.get(event.jobId());
        if (subs == null) {
            return;
        }
        subs.forEach(s -> s.onLive(event));
    }

    int subscriberCount(UUID jobId) {
        List<Subscription> subs = subscriptions.get(jobId);
        return subs == null ? 0 : subs.size();
    }

    @Scheduled(fixedRate = KEEP_ALIVE_MS)
    void keepAlive() {
        subscriptions.values().forEach(list -> list.forEach(Subscription::keepAlive));
    }

    private void remove(Subscription subscription) {
        subscriptions.computeIfPresent(subscription.jobId, (id, list) -> {
            list.remove(subscription);
            return list.isEmpty() ? null : list;
        });
    }

    /** One connected client. All sends are serialised on the instance monitor. */
    final class Subscription {

        private final UUID jobId;
        private final SseEmitter emitter;
        private final List<JobStageEvent> buffered = new ArrayList<>();
        private int lastSent;
        private boolean live;
        private boolean closed;

        private Subscription(UUID jobId, SseEmitter emitter, int lastSeenSequence) {
            this.jobId = jobId;
            this.emitter = emitter;
            this.lastSent = lastSeenSequence;
        }

        SseEmitter emitter() {
            return emitter;
        }

        /** Sends persisted history, then everything that arrived live meanwhile, in order. */
        synchronized void replayThenGoLive(List<JobStageEvent> persisted) {
            persisted.forEach(this::send);
            buffered.sort(Comparator.comparingInt(JobStageEvent::sequence));
            buffered.forEach(this::send);
            buffered.clear();
            live = true;
        }

        synchronized void onLive(JobStageEvent event) {
            if (live) {
                send(event);
            } else {
                buffered.add(event);
            }
        }

        synchronized void close() {
            if (!closed) {
                closed = true;
                emitter.complete();
            }
        }

        synchronized void keepAlive() {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                closed = true;
                remove(this);
            }
        }

        private void send(JobStageEvent event) {
            if (closed || event.sequence() <= lastSent) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(Integer.toString(event.sequence()))
                        .name("stage")
                        .data(event, MediaType.APPLICATION_JSON));
                lastSent = event.sequence();
                if (event.stage().terminal()) {
                    close();
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE subscriber for job {} went away: {}", jobId, e.toString());
                closed = true;
                remove(this);
            }
        }
    }
}
