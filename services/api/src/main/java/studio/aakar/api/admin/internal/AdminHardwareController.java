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
import studio.aakar.api.catalog.HardwareItemDto;
import studio.aakar.api.catalog.HardwareItemInput;
import studio.aakar.api.shared.ApiProblemException;

/** Bought-in hardware (split rings, magnets, cords, LED bases…) with costs and weights: what family defaults and template descriptors reference by SKU. */
@RestController
@RequestMapping("/admin/api/hardware")
@Tag(name = "admin · hardware")
@SecurityRequirement(name = "staffBearer")
class AdminHardwareController {

    private final Catalog catalog;
    private final AuditLog audit;

    AdminHardwareController(Catalog catalog, AuditLog audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Bought-in hardware items, available or not")
    List<HardwareItemDto> all(StaffPrincipal staff) {
        return catalog.hardware();
    }

    @PostMapping
    @Operation(summary = "Create a hardware item", description = "Owner only. 409 `hardware_exists` for a taken SKU. Audited as `hardware.create`.")
    ResponseEntity<HardwareItemDto> create(@Valid @RequestBody HardwareItemInput input, StaffPrincipal staff) {
        staff.requireOwner();
        HardwareItemDto created = catalog.createHardware(input);
        audit.record(staff.email(), AuditLog.HARDWARE_CREATE, created.sku(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{sku}")
    @Operation(summary = "Update a hardware item", description = "Owner only. 404 for an unknown SKU; `available: false` keeps the item for existing "
            + "families but flags it in the portal. Audited as `hardware.update`.")
    HardwareItemDto update(@PathVariable String sku, @Valid @RequestBody HardwareItemInput input, StaffPrincipal staff) {
        staff.requireOwner();
        HardwareItemDto before = catalog.hardwareItem(sku).orElseThrow(() -> ApiProblemException.notFound("Hardware item", sku));
        HardwareItemDto after = catalog.updateHardware(sku, input);
        audit.record(staff.email(), AuditLog.HARDWARE_UPDATE, sku, before, after);
        return after;
    }
}
