package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.templates.MotifDto;
import studio.aakar.api.templates.Motifs;

/** The motif library (Buti) in the portal, read-only: the library is files in {@code packages/design-tokens/motifs}. */
@RestController
@RequestMapping("/admin/api/motifs")
@Tag(name = "admin · motifs")
@SecurityRequirement(name = "staffBearer")
class AdminMotifsController {

    private final Motifs motifs;

    AdminMotifsController(Motifs motifs) {
        this.motifs = motifs;
    }

    @GetMapping
    @Operation(summary = "The Buti motif library, for experience motif packs and template previews",
            description = "Any staff role. The storefront's `Motif` rows in library order, `svg_url` included.")
    List<MotifDto> all(StaffPrincipal staff) {
        return motifs.list();
    }
}
