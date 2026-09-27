package studio.aakar.api.design;

import java.util.Optional;
import java.util.UUID;

/** Public API of the design module for the cart, order and identity modules. */
public interface Designs {

    Optional<DesignResponse> find(UUID designId);

    /** A version with its assets, printability, estimate and price; empty when unknown. */
    Optional<DesignVersionResponse> findVersion(UUID versionId);

    /**
     * Moves every design the guest started to the user (sign-in hand-over).
     *
     * @return how many designs changed owner
     */
    int attachGuest(UUID guestId, UUID userId);
}
