package studio.aakar.api.cart;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code Cart} in the OpenAPI document. */
public record CartDto(
        UUID id,
        String owner,
        List<CartItemDto> items,
        long subtotalPaise,
        long shippingPaise,
        String shippingLabel,
        long totalPaise,
        String policyVersion,
        Instant updatedAt) {

    @JsonIgnore
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
