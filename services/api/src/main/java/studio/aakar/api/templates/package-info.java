/**
 * Template descriptors published by the geometry service, cached for 60 s, plus validation of
 * parameter values against a descriptor's ranges and types (never silently clamped), and the staff
 * live switch per template ({@code template_flags}, ADR-0012): a template switched off disappears from
 * {@code GET /api/templates} and refuses new designs.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Templates")
package studio.aakar.api.templates;
