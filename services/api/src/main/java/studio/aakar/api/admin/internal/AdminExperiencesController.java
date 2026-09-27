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
import studio.aakar.api.catalog.AdminExperienceDto;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.EnvironmentDto;
import studio.aakar.api.catalog.ExperienceInput;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Experiences (Duniya) in the portal: codename, slug, copy, backdrop, style, motif pack, ordered avatars, curated items,
 * collections, seasons and the availability switch; plus the backdrops (Mahaul) as read-only reference data.
 */
@RestController
@RequestMapping("/admin/api")
@Tag(name = "admin · experiences")
@SecurityRequirement(name = "staffBearer")
class AdminExperiencesController {

    private final Catalog catalog;
    private final AuditLog audit;

    AdminExperiencesController(Catalog catalog, AuditLog audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping("/experiences")
    @Operation(summary = "Every experience (Duniya), available or not, in display order",
            description = "Rows as stored: `avatars` are family ids and `items` Shop item slugs, in order, whether or not they can be ordered today.")
    List<AdminExperienceDto> all(StaffPrincipal staff) {
        return catalog.allExperiences();
    }

    @PostMapping("/experiences")
    @Operation(summary = "Create an experience", description = "Owner only. 409 `experience_exists` for a taken id, `slug_exists` for a taken "
            + "slug; 422 `validation_failed` for an unknown environment (the detail names the backdrops) or Shop item, a repeated avatar, item, "
            + "motif or collection, or a season window that is not two dates in order or two month-days; 422 `unknown_family` for an avatar "
            + "that is not a family. Audited as `experience.create`.")
    ResponseEntity<AdminExperienceDto> create(@Valid @RequestBody ExperienceInput input, StaffPrincipal staff) {
        staff.requireOwner();
        AdminExperienceDto created = catalog.createExperience(input);
        audit.record(staff.email(), AuditLog.EXPERIENCE_CREATE, created.id(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/experiences/{experienceId}")
    @Operation(summary = "Update an experience's copy, slug, backdrop, style, motif pack, avatars, items, collections, seasons or availability",
            description = "Owner only. The id in the path wins over the body. 404 `unknown_experience`; 409 `slug_exists` when another "
                    + "experience has the slug; 422 as on create. Audited as `experience.update`.")
    AdminExperienceDto update(@PathVariable String experienceId, @Valid @RequestBody ExperienceInput input, StaffPrincipal staff) {
        staff.requireOwner();
        AdminExperienceDto before = catalog.adminExperience(experienceId).orElseThrow(() -> ApiProblemException.notFound(
                ProblemCodes.UNKNOWN_EXPERIENCE, "Unknown experience", "Experience " + experienceId + " was not found"));
        AdminExperienceDto after = catalog.updateExperience(experienceId, input);
        audit.record(staff.email(), AuditLog.EXPERIENCE_UPDATE, experienceId, before, after);
        return after;
    }

    @GetMapping("/environments")
    @Operation(summary = "Viewer backdrops (Mahaul) in display order, read-only",
            description = "The valid `environment` values for experiences, families and Shop items; a new backdrop needs a storefront preset first.")
    List<EnvironmentDto> environments(StaffPrincipal staff) {
        return catalog.environments();
    }
}
