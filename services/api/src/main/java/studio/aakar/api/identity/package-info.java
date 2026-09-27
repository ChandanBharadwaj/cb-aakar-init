/**
 * Customer identity (PLAN §6, ADR-0013): phone OTP sign-in through an {@link studio.aakar.api.identity.OtpSender}
 * adapter (mock by default), HS256 access tokens whose {@code jti} is a revocable row in {@code sessions},
 * the stateless Spring Security chain that resolves every request to a {@link studio.aakar.api.shared.Identity}
 * (user, guest or anonymous), addresses, and the sign-in hand-over that attaches a guest's designs and cart to
 * the user.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package studio.aakar.api.identity;
