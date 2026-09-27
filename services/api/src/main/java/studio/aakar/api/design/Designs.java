package studio.aakar.api.design;

import java.util.Optional;
import java.util.UUID;
import studio.aakar.api.pricing.PriceInputs;

/** Public API of the design module for the cart, order, identity and admin modules. */
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

    /**
     * What a version is beyond its print, for the price calculator: its family ({@code spec.family}, whose rules add a
     * setup fee and a minimum) and the hardware packed with each piece with unit costs from the catalog. The version's
     * hardware was resolved when it was created (template descriptor, then family default) and replaced by the geometry
     * result's {@code hardware} when that reports any.
     */
    PriceInputs.Context priceContext(DesignVersionResponse version);
}
