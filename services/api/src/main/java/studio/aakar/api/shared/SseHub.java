package studio.aakar.api.shared;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of Server-Sent Events subscribers per stream key (a job id, an order id). Every
 * event is sent as {@code event: <eventName>} with {@code id} = its sequence, so clients resume with
 * {@code Last-Event-ID}. A subscription buffers live events until the controller has replayed the
 * persisted history, then flushes in sequence order; anything at or below the last sent sequence is
 * dropped, so replay and live delivery never duplicate. {@link SseKeepAlive} sends a {@code : keep-alive}
 * comment every 15 s; a stream completes after a terminal event.
 *
 * @param <E> the event payload written as JSON
 */
public final class SseHub<E> {

    public static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final String eventName;
    private final ToIntFunction<E> sequence;
    private final Predicate<E> terminal;
    private final Map<UUID, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

    /**
     * @param eventName the SSE {@code event:} name
     * @param sequence per-key increasing sequence of an event (the SSE {@code id})
     * @param terminal whether an event ends the stream
     */
    public SseHub(String eventName, ToIntFunction<E> sequence, Predicate<E> terminal) {
        this.eventName = eventName;
        this.sequence = sequence;
        this.terminal = terminal;
    }

    public Subscription subscribe(UUID key, int lastSeenSequence) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        Subscription subscription = new Subscription(key, emitter, lastSeenSequence);
        subscriptions.computeIfAbsent(key, id -> new CopyOnWriteArrayList<>()).add(subscription);
        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> {
            remove(subscription);
            emitter.complete();
        });
        emitter.onError(e -> remove(subscription));
        return subscription;
    }

    /** Delivers a live event to every subscriber of {@code key}; a no-op when nobody listens. */
    public void publish(UUID key, E event) {
        List<Subscription> subs = subscriptions.get(key);
        if (subs == null) {
            return;
        }
        subs.forEach(s -> s.onLive(event));
    }

    public int subscriberCount(UUID key) {
        List<Subscription> subs = subscriptions.get(key);
        return subs == null ? 0 : subs.size();
    }

    public void keepAlive() {
        subscriptions.values().forEach(list -> list.forEach(Subscription::keepAlive));
    }

    private void remove(Subscription subscription) {
        subscriptions.computeIfPresent(subscription.key, (id, list) -> {
            list.remove(subscription);
            return list.isEmpty() ? null : list;
        });
    }

    /** One connected client. All sends are serialised on the instance monitor. */
    public final class Subscription {

        private final UUID key;
        private final SseEmitter emitter;
        private final List<E> buffered = new ArrayList<>();
        private int lastSent;
        private boolean live;
        private boolean closed;

        private Subscription(UUID key, SseEmitter emitter, int lastSeenSequence) {
            this.key = key;
            this.emitter = emitter;
            this.lastSent = lastSeenSequence;
        }

        public SseEmitter emitter() {
            return emitter;
        }

        /** Sends persisted history, then everything that arrived live meanwhile, in order. */
        public synchronized void replayThenGoLive(List<E> persisted) {
            persisted.forEach(this::send);
            buffered.sort(Comparator.comparingInt(sequence));
            buffered.forEach(this::send);
            buffered.clear();
            live = true;
        }

        synchronized void onLive(E event) {
            if (live) {
                send(event);
            } else {
                buffered.add(event);
            }
        }

        public synchronized void close() {
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

        private void send(E event) {
            int seq = sequence.applyAsInt(event);
            if (closed || seq <= lastSent) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(Integer.toString(seq))
                        .name(eventName)
                        .data(event, MediaType.APPLICATION_JSON));
                lastSent = seq;
                if (terminal.test(event)) {
                    close();
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE subscriber for {} went away: {}", key, e.toString());
                closed = true;
                remove(this);
            }
        }
    }
}
