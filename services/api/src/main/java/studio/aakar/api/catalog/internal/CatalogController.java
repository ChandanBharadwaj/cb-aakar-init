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
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.shared.ApiProblemException;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "catalog")
class CatalogController {

    private final Catalog catalog;

    CatalogController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/items")
    @Operation(summary = "List Shop items", description = "Optional filters: `category` (home_decor, nameplates, kitchen, desk_tech, gifting) and free-text `q`.")
    List<CatalogItemDto> items(@RequestParam(required = false) String category, @RequestParam(required = false) String q) {
        return catalog.items(category, q);
    }

    @GetMapping("/items/{slug}")
    @Operation(summary = "Shop item by slug")
    CatalogItemDto item(@PathVariable String slug) {
        return catalog.item(slug).orElseThrow(() -> ApiProblemException.notFound("Catalog item", slug));
    }

    @GetMapping("/materials")
    @Operation(summary = "Digital materials with rendering presets and rates")
    List<MaterialDto> materials() {
        return catalog.materials();
    }
}
