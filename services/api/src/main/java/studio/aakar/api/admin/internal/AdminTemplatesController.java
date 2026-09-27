package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@RestController
@RequestMapping("/admin/api/templates")
@Tag(name = "admin · templates")
@SecurityRequirement(name = "staffBearer")
class AdminTemplatesController {

    private final Templates templates;
    private final Catalog catalog;
    private final AuditLog audit;

    AdminTemplatesController(Templates templates, Catalog catalog, AuditLog audit) {
        this.templates = templates;
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Templates known to the geometry service with their live flag and the Shop items using them")
    List<AdminTemplateDto> all(StaffPrincipal staff) {
        Map<String, Boolean> flags = templates.liveFlags();
        List<CatalogItemDto> items = catalog.items(null, null);
        return templates.allKnown().stream().map(d -> AdminTemplateDto.of(d, flags.getOrDefault(d.id(), true), items)).toList();
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Switch a template on or off", description = "Owner only. `live: false` hides it from `GET /api/templates` and makes "
            + "`POST /api/designs` answer 422 `template_not_available`. 404 for a template the geometry service does not publish.")
    AdminTemplateDto setLive(@PathVariable String templateId, @Valid @RequestBody LiveRequest request, StaffPrincipal staff) {
        staff.requireOwner();
        boolean before = templates.isLive(templateId);
        TemplateDescriptor descriptor = templates.setLive(templateId, request.live());
        audit.record(staff.email(), AuditLog.TEMPLATE_LIVE, descriptor.id(), Map.of("live", before), Map.of("live", request.live()));
        return AdminTemplateDto.of(descriptor, request.live(), catalog.items(null, null));
    }

    record LiveRequest(@NotNull(message = "live is required") Boolean live) {
    }

    /** One row of {@code GET /admin/api/templates}. */
    record AdminTemplateDto(String id, int version, String family, String name, boolean live, List<String> catalogItems,
            List<String> featuresSupported, List<TemplateDescriptor.Hardware> hardware) {

        static AdminTemplateDto of(TemplateDescriptor d, boolean live, List<CatalogItemDto> items) {
            return new AdminTemplateDto(d.id(), d.version(), d.family(), d.name(), live,
                    items.stream().filter(i -> d.id().equals(i.templateId())).map(CatalogItemDto::slug).toList(),
                    d.featuresSupportedOrEmpty(), d.hardware() == null ? List.of() : d.hardware());
        }
    }
}
