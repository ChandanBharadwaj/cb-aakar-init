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
import studio.aakar.api.catalog.FamilyDto;
import studio.aakar.api.catalog.FamilyInput;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/** Outcome families (Avatars) in the portal: copy, tier, shelf, envelope, hardware default, rules, content slot and the availability switch. */
@RestController
@RequestMapping("/admin/api/families")
@Tag(name = "admin · families")
@SecurityRequirement(name = "staffBearer")
class AdminFamiliesController {

    private final Catalog catalog;
    private final AuditLog audit;

    AdminFamiliesController(Catalog catalog, AuditLog audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Every outcome family (Avatar), available or not, with readiness",
            description = "`ready` is true when at least one live template of the family exists in the geometry service; `template_ids` names them.")
    List<FamilyDto> all(StaffPrincipal staff) {
        return catalog.allFamilies().stream().map(FamilyDto::withoutTemplates).toList();
    }

    @PostMapping
    @Operation(summary = "Create a family", description = "Owner only. 409 `family_exists` for a taken id; 422 `validation_failed` for an unknown "
            + "shelf or an envelope with min > max; 422 `unknown_hardware` / `unknown_material` for unknown SKUs or allowed materials. "
            + "Audited as `family.create`.")
    ResponseEntity<FamilyDto> create(@Valid @RequestBody FamilyInput input, StaffPrincipal staff) {
        staff.requireOwner();
        FamilyDto created = catalog.createFamily(input);
        audit.record(staff.email(), AuditLog.FAMILY_CREATE, created.id(), null, created.withoutTemplates());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{familyId}")
    @Operation(summary = "Update a family's copy, tier, shelf, envelope, hardware, rules, content slot or availability",
            description = "Owner only. The id in the path wins over the body. 404 `unknown_family` for an unknown id. Audited as `family.update`.")
    FamilyDto update(@PathVariable String familyId, @Valid @RequestBody FamilyInput input, StaffPrincipal staff) {
        staff.requireOwner();
        FamilyDto before = catalog.family(familyId).orElseThrow(() -> ApiProblemException.notFound(ProblemCodes.UNKNOWN_FAMILY, "Unknown family",
                "Family " + familyId + " was not found"));
        FamilyDto after = catalog.updateFamily(familyId, input);
        audit.record(staff.email(), AuditLog.FAMILY_UPDATE, familyId, before.withoutTemplates(), after.withoutTemplates());
        return after;
    }
}
