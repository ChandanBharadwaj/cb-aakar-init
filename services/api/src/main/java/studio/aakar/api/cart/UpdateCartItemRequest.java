package studio.aakar.api.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of {@code PATCH /api/cart/items/{itemId}}: a new quantity and/or a new finish. */
public record UpdateCartItemRequest(
        @Min(value = 1, message = "qty must be at least 1") @Max(value = 20, message = "qty must be at most 20") Integer qty,
        String material) {

    public boolean changesMaterial() {
        return material != null && !material.isBlank();
    }
}
