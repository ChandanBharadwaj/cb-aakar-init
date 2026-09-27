package studio.aakar.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public read API for a customer's addresses (checkout snapshots one into the order). */
public interface Addresses {

    List<AddressDto> list(UUID userId);

    /** The address only when it belongs to {@code userId}. */
    Optional<AddressDto> find(UUID userId, UUID addressId);
}
