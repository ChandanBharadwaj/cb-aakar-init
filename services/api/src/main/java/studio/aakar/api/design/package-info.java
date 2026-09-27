/**
 * Designs and their immutable versions. Every edit is a new version with a parent; nothing is
 * mutated except a version's status and results as its generation job completes. A design belongs
 * to the {@link studio.aakar.api.shared.Identity} that started it (user or guest) and stays readable by
 * its unguessable id; {@link studio.aakar.api.design.Designs#attachGuest} moves guest designs to a user.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Design")
package studio.aakar.api.design;
