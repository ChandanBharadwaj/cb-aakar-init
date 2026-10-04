package studio.aakar.api.templates;

import java.util.List;
import java.util.Optional;

/**
 * The motif library (Buti): {@code index.json} and one single-path SVG per motif, read once at startup from
 * {@code aakar.motifs.dir} (the same folder the geometry service builds motifs from) and cached. Behind
 * {@code GET /api/motifs}, {@code GET /api/motifs/{id}.svg}, {@code GET /admin/api/motifs} and the motif checks of
 * {@link Templates#validateFeatures}.
 */
public interface Motifs {

    /** Every motif in library order; empty when the library could not be loaded (a local run without it). */
    List<MotifDto> list();

    /** The motif a {@code motif_id} names. */
    Optional<MotifDto> find(String id);

    /** The motif's artwork: a single-path SVG document. */
    Optional<byte[]> svg(String id);

    /**
     * {@code false} when the library could not be loaded. That only happens locally (a production start fails instead);
     * the feature checks then leave motif ids and scales to the geometry service.
     */
    boolean available();
}
