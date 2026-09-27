/**
 * Template descriptors published by the geometry service, cached for 60 s, plus validation of
 * parameter values against a descriptor's ranges and types (never silently clamped) and of content
 * features (the Chhaap) the way the geometry service checks them, and the staff live switch per template
 * ({@code template_flags}, ADR-0012): a template switched off disappears from {@code GET /api/templates} and
 * refuses new designs. Also the motif library (Buti, {@link studio.aakar.api.templates.Motifs}): the files of
 * {@code packages/design-tokens/motifs}, served at {@code GET /api/motifs} and checked on every motif feature.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Templates")
package studio.aakar.api.templates;
