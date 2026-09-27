package studio.aakar.api.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /api/cart/items}. */
public record AddCartItemRequest(
        @NotNull(message = "version_id is required") UUID versionId,
        @NotBlank(message = "material is required") String material,
        @Min(value = 1, message = "qty must be at least 1") @Max(value = 20, message = "qty must be at most 20") Integer qty) {

    public static final int MAX_QTY = 20;

    public int qtyOrOne() {
        return qty == null ? 1 : qty;
    }
}
