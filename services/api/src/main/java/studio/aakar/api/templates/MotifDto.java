package studio.aakar.api.templates;

import java.util.List;

/**
 * One motif (Buti) of the library ({@code packages/design-tokens/motifs/index.json}): the {@code Motif} of the storefront
 * contract. {@code id} is the {@code motif_id} of a {@code motif} feature (snake_case, never renamed), {@code label} its plain
 * name ("Paisley"), {@code minScale} the smallest {@code scale} that still prints (strokes and openings at least 0.8 mm; scale 1
 * fills the anchor's spot) and {@code svgUrl} the browser URL of its artwork, {@code {aakar.api.public-url}/api/motifs/{id}.svg}.
 */
public record MotifDto(String id, String label, List<String> tags, double minScale, String svgUrl) {

    public MotifDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
