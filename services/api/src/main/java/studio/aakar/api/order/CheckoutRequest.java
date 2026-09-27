package studio.aakar.api.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of {@code POST /api/checkout}. */
public record CheckoutRequest(
        @NotNull(message = "address_id is required") UUID addressId,
        Boolean notifyWhatsapp,
        @Size(max = 200, message = "note must be at most 200 characters") String note) {

    public boolean notifyWhatsappOrDefault() {
        return notifyWhatsapp == null || notifyWhatsapp;
    }
}
