package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shared.PageDto;

@RestController
@RequestMapping("/admin/api/audit")
@Tag(name = "admin · audit")
@SecurityRequirement(name = "staffBearer")
class AuditController {

    private final AuditLog audit;

    AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Audit log, newest first")
    PageDto<AuditEntryDto> page(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, StaffPrincipal staff) {
        return audit.page(page, size);
    }
}
