/**
 * Price breakdowns (PLAN §7.10): pure functions over a versioned {@link studio.aakar.api.pricing.PricingPolicy}
 * served by the {@link studio.aakar.api.pricing.PricingPolicyStore} (ADR-0008; {@code aakar.pricing.*} is only the
 * seed). All amounts are integer paise.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Pricing")
package studio.aakar.api.pricing;
