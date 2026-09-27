package studio.aakar.api.shared;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends the {@code : keep-alive} comment on every {@link SseHub} bean so proxies keep idle streams open. */
@Component
class SseKeepAlive {

    static final long KEEP_ALIVE_MS = 15_000;

    private final ObjectProvider<SseHub<?>> hubs;

    SseKeepAlive(ObjectProvider<SseHub<?>> hubs) {
        this.hubs = hubs;
    }

    @Scheduled(fixedRate = KEEP_ALIVE_MS)
    void keepAlive() {
        hubs.stream().forEach(SseHub::keepAlive);
    }
}
