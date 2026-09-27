package studio.aakar.api.order.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.shared.SseHub;

/** The tracking page's SSE hub: {@code event: stage}, id = sequence, closes after delivered/cancelled. */
@Configuration
class OrderEventStreams {

    @Bean
    SseHub<OrderEventDto> orderEventHub() {
        return new SseHub<>("stage", OrderEventDto::sequence, event -> event.status().terminal());
    }
}
