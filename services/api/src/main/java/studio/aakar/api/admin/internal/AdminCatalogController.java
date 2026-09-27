package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.CatalogItemInput;
import studio.aakar.api.shared.ApiProblemException;

@RestController
@RequestMapping("/admin/api/catalog/items")
@Tag(name = "admin · catalog")
@SecurityRequirement(name = "staffBearer")
class AdminCatalogController {

    private final Catalog catalog;
    private final AuditLog audit;

    AdminCatalogController(Catalog catalog, AuditLog audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Every Shop item, including unavailable ones")
    List<CatalogItemDto> all(StaffPrincipal staff) {
        return catalog.items(null, null);
    }

    @PostMapping
    @Operation(summary = "Add a Shop item", description = "Owner only. 409 `slug_exists` for a taken slug; 422 `unknown_material` for an unknown default material.")
    ResponseEntity<CatalogItemDto> create(@Valid @RequestBody CatalogItemInput input, StaffPrincipal staff) {
        staff.requireOwner();
        CatalogItemDto created = catalog.createItem(input);
        audit.record(staff.email(), AuditLog.CATALOG_CREATE, created.slug(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{slug}")
    @Operation(summary = "Replace a Shop item", description = "Owner only. 404 for an unknown slug.")
    CatalogItemDto update(@PathVariable String slug, @Valid @RequestBody CatalogItemInput input, StaffPrincipal staff) {
        staff.requireOwner();
        CatalogItemDto before = catalog.item(slug).orElseThrow(() -> ApiProblemException.notFound("Catalog item", slug));
        CatalogItemDto after = catalog.updateItem(slug, input);
        audit.record(staff.email(), AuditLog.CATALOG_UPDATE, slug, before, after);
        return after;
    }
}
