package studio.aakar.api.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read API of the identity module for other modules (orders need the customer's phone; the management
 * API shows the customer on every order and searches the queue by phone).
 */
public interface Users {

    Optional<UserDto> find(UUID userId);

    /** The users among {@code userIds} that exist, keyed by id. */
    Map<UUID, UserDto> findAll(Collection<UUID> userIds);

    /** Ids of users whose phone contains {@code digits} (e.g. {@code 98765} matches {@code +919876543210}). */
    List<UUID> findIdsByPhoneContaining(String digits);
}
