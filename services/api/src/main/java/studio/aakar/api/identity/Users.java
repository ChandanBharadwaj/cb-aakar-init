package studio.aakar.api.identity;

import java.util.Optional;
import java.util.UUID;

/** Public read API of the identity module for other modules (orders need the customer's phone). */
public interface Users {

    Optional<UserDto> find(UUID userId);
}
