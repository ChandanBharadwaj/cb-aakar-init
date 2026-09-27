package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin · dashboard")
@SecurityRequirement(name = "staffBearer")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/admin/api/dashboard")
    @Operation(summary = "Counts and money for the studio board",
            description = "Orders per status, orders placed today, paid revenue today and this month (Asia/Kolkata days), and the number of "
                    + "orders in queued / finishing / qc that need a staff step.")
    DashboardDto dashboard(StaffPrincipal staff) {
        return dashboard.dashboard();
    }
}
