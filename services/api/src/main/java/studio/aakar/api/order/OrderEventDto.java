package studio.aakar.api.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** {@code OrderEvent} in the OpenAPI document: the {@code data} of every SSE {@code stage} event on an order stream. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderEventDto(int sequence, OrderStatus status, OrderStage stage, String message, Map<String, Object> detail, Instant at) {
}
