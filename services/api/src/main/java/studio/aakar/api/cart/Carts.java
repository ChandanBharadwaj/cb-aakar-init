package studio.aakar.api.cart;

import java.util.UUID;
import studio.aakar.api.shared.Identity;

/** Public API of the cart module. */
public interface Carts {

    /** The identity's cart (created on first use), re-priced against the active policy. */
    CartDto cart(Identity identity);

    CartDto add(Identity identity, AddCartItemRequest request);

    CartDto update(Identity identity, UUID itemId, UpdateCartItemRequest request);

    CartDto remove(Identity identity, UUID itemId);

    CartDto clear(Identity identity);

    /**
     * Sign-in hand-over: moves the guest cart's items into the user's cart. The same version and material
     * on both sides add up (capped at 20); the guest cart is then deleted.
     *
     * @return how many guest items were moved or combined
     */
    int mergeGuestCart(UUID guestId, UUID userId);
}
