/**
 * Cross-cutting infrastructure shared by every module: Problem Details, JSON configuration, CORS,
 * HTTP client helpers, the {@code aakar.*} configuration properties, the per-request
 * {@link studio.aakar.api.shared.Identity}, the generic {@link studio.aakar.api.shared.SseHub} and the
 * production guard against mock adapters.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN, displayName = "Shared")
package studio.aakar.api.shared;
