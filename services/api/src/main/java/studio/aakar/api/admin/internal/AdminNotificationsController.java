package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.notification.NotificationDto;
import studio.aakar.api.notification.Notifications;
import studio.aakar.api.shared.PageDto;

@RestController
@RequestMapping("/admin/api/notifications")
@Tag(name = "admin · messages")
@SecurityRequirement(name = "staffBearer")
class AdminNotificationsController {

    private final Notifications notifications;

    AdminNotificationsController(Notifications notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Messages log (what the mock sender would have sent), newest first")
    PageDto<NotificationDto> page(@RequestParam(name = "order_id", required = false) UUID orderId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, StaffPrincipal staff) {
        return notifications.page(orderId, page, size);
    }
}
