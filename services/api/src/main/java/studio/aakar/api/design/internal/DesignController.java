package studio.aakar.api.design.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.design.CreateDesignRequest;
import studio.aakar.api.design.DesignAccepted;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.EditParamsRequest;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.shared.Identity;

@RestController
@RequestMapping("/api")
class DesignController {

    private final DesignService designs;

    DesignController(DesignService designs) {
        this.designs = designs;
    }

    @PostMapping("/designs")
    @Tag(name = "designs")
    @Operation(summary = "Start a design", description = """
            Shop path: `catalog_item_slug` (template and defaults come from the item). \
            Remix-lite / direct path: `template_id` + optional `params`. \
            Create path: `prompt` → 422 `not_yet_available` until Phase 2. \
            Returns 202 with the design and the first generation job; follow `events_url`. \
            The design belongs to the bearer token's user or to the `X-Aakar-Guest` identity.""")
    ResponseEntity<DesignAccepted> create(@Valid @RequestBody CreateDesignRequest request, Identity identity) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(designs.create(request, identity));
    }

    @GetMapping("/designs/{id}")
    @Tag(name = "designs")
    @Operation(summary = "Design with latest version")
    DesignResponse design(@PathVariable UUID id) {
        return designs.get(id);
    }

    @GetMapping("/designs/{id}/versions")
    @Tag(name = "designs")
    @Operation(summary = "Version history, newest first")
    List<DesignVersionResponse> versions(@PathVariable UUID id) {
        return designs.versions(id);
    }

    @GetMapping("/versions/{versionId}")
    @Tag(name = "versions")
    @Operation(summary = "Version")
    DesignVersionResponse version(@PathVariable UUID versionId) {
        return designs.version(versionId);
    }

    @PostMapping("/versions/{versionId}/params")
    @Tag(name = "versions")
    @Operation(summary = "Edit parameters → new version (new job)")
    ResponseEntity<DesignAccepted> editParams(@PathVariable UUID versionId, @Valid @RequestBody EditParamsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(designs.editParams(versionId, request));
    }

    @GetMapping("/versions/{versionId}/printability")
    @Tag(name = "versions")
    @Operation(summary = "Stability report", description = "409 `version_not_ready` while the version is generating.")
    Map<String, Object> printability(@PathVariable UUID versionId) {
        return designs.printability(versionId);
    }

    @GetMapping("/versions/{versionId}/price")
    @Tag(name = "versions")
    @Operation(summary = "Price breakdown for a material", description = "409 `version_not_ready` while generating; 404 `unknown_material` for an unknown material.")
    PriceBreakdown price(@PathVariable UUID versionId, @RequestParam String material) {
        return designs.price(versionId, material);
    }
}
