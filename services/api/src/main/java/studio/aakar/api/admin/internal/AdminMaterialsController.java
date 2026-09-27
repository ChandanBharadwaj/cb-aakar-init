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
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.catalog.MaterialInput;
import studio.aakar.api.shared.ApiProblemException;

@RestController
@RequestMapping("/admin/api/materials")
@Tag(name = "admin · materials")
@SecurityRequirement(name = "staffBearer")
class AdminMaterialsController {

    private final Catalog catalog;
    private final AuditLog audit;

    AdminMaterialsController(Catalog catalog, AuditLog audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Every material, including unavailable ones")
    List<MaterialDto> all(StaffPrincipal staff) {
        return catalog.allMaterials();
    }

    @PostMapping
    @Operation(summary = "Add a material", description = "Owner only. 409 `material_exists` for a taken id.")
    ResponseEntity<MaterialDto> create(@Valid @RequestBody MaterialInput input, StaffPrincipal staff) {
        staff.requireOwner();
        MaterialDto created = catalog.createMaterial(input);
        audit.record(staff.email(), AuditLog.MATERIAL_CREATE, created.id(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{materialId}")
    @Operation(summary = "Replace a material", description = "Owner only. 404 for an unknown id; `available: false` hides it from the storefront.")
    MaterialDto update(@PathVariable String materialId, @Valid @RequestBody MaterialInput input, StaffPrincipal staff) {
        staff.requireOwner();
        MaterialDto before = catalog.material(materialId).orElseThrow(() -> ApiProblemException.notFound("Material", materialId));
        MaterialDto after = catalog.updateMaterial(materialId, input);
        audit.record(staff.email(), AuditLog.MATERIAL_UPDATE, materialId, before, after);
        return after;
    }
}
