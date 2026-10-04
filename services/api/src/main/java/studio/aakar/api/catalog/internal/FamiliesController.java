package studio.aakar.api.catalog.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.FamilyDto;

/** Outcome families (Avatars) for the storefront's Create picker: "Give your idea an Avatar". */
@RestController
@RequestMapping("/api/families")
@Tag(name = "families")
class FamiliesController {

    private final Catalog catalog;

    FamiliesController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Outcome categories (Avatars) that are available and have a live template",
            description = "Each family carries its consumer copy, size envelope, bought-in hardware, material rules and content slot (Chhaap), "
                    + "plus the live template descriptors of that family. Optional `kind` filter: carrier, object or raw (the raw family, Swaroop, "
                    + "is included; Shop shelves never list it). Needs the geometry service for readiness, like `GET /api/templates`.")
    List<FamilyDto> list(@RequestParam(required = false) String kind) {
        return catalog.families(kind, false);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Outcome family by id", description = "Any seeded family, available or not (`available` and `ready` say whether it can be "
            + "ordered today); 404 `unknown_family` otherwise.")
    FamilyDto byId(@PathVariable String id) {
        return catalog.family(id).orElseThrow(() -> CatalogService.unknownFamily(id));
    }
}
