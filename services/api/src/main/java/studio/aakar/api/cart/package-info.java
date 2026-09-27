/**
 * Carts for guests and users (PLAN §6). One cart per {@link studio.aakar.api.shared.Identity}; a guest cart
 * merges into the user's on sign-in. Items snapshot their unit price from the active pricing policy and are
 * re-priced on read when the policy changed. The cart empties itself when a payment for its owner succeeds
 * ({@code PaymentSucceeded} from the payment module).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cart")
package studio.aakar.api.cart;
