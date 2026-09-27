/**
 * Designs and their immutable versions. Every edit is a new version with a parent; nothing is
 * mutated except a version's status and results as its generation job completes. A design belongs
 * to the {@link studio.aakar.api.shared.Identity} that started it (user or guest) and stays readable by
 * its unguessable id; {@link studio.aakar.api.design.Designs#attachGuest} moves guest designs to a user.
 *
 * <p>A design may name its outcome family (Avatar) and carry content features (the Chhaap: a photo relief, text, a
 * motif or the customer's own form) whose sources are the customer's ready uploads; the raw print family (Swaroop,
 * ADR-0014) carries exactly one form sized by its longest side. Versions record the bought-in hardware packed with each
 * piece, and {@link studio.aakar.api.design.Designs#priceContext} hands family and hardware to the price calculator.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Design")
package studio.aakar.api.design;
