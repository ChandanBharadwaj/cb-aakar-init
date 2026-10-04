package studio.aakar.api.templates.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.MotifDto;
import studio.aakar.api.templates.Motifs;

/** The motif library (Buti) for the Chhaap panel: the list with preview URLs, and each motif's artwork. */
@RestController
@RequestMapping("/api/motifs")
@Tag(name = "motifs")
class MotifsController {

    static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
    /** Artwork changes rarely (a motif id is never renamed); browsers keep it a day, then revalidate by ETag. */
    static final CacheControl SVG_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();
    /** Shown through {@code <img>} and inline previews; opened on its own the document may load and run nothing. */
    static final String SVG_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final Motifs motifs;

    MotifsController(Motifs motifs) {
        this.motifs = motifs;
    }

    @GetMapping
    @Operation(summary = "The Buti motif library with preview URLs", description = "Motifs a customer can place with a `motif` feature, "
            + "in library order. `min_scale` is the smallest scale that still prints; scale 1 fills the anchor's spot. The artwork is at `svg_url`.")
    List<MotifDto> list() {
        return motifs.list();
    }

    @GetMapping("/{id}.svg")
    @Operation(summary = "Motif artwork (single-path SVG) for previews", description = "Cached for a day with an ETag; 404 `unknown_motif`.")
    ResponseEntity<byte[]> svg(@PathVariable String id) {
        byte[] svg = motifs.svg(id).orElseThrow(() -> unknownMotif(id));
        return ResponseEntity.ok()
                .contentType(SVG)
                .cacheControl(SVG_CACHE)
                .eTag(DigestUtils.md5DigestAsHex(svg))
                .header("Content-Security-Policy", SVG_POLICY)
                .body(svg);
    }

    static ApiProblemException unknownMotif(String id) {
        return ApiProblemException.notFound(ProblemCodes.UNKNOWN_MOTIF, "Unknown motif", "Motif " + id + " was not found");
    }
}
