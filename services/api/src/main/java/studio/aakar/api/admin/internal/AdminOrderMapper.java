package studio.aakar.api.admin.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.notification.NotificationDto;
import studio.aakar.api.order.StaffOrder;
import studio.aakar.api.order.StaffOrderSummary;

/**
 * {@code AdminOrderSummary} and {@code AdminOrder}: the customer-facing JSON of an order plus {@code customer},
 * {@code next_actions}, and for the full view {@code qc_photos} and {@code notifications}. Built as maps so the
 * customer shape is reused verbatim.
 */
@Component
class AdminOrderMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final ObjectMapper json;

    AdminOrderMapper(ObjectMapper json) {
        this.json = json;
    }

    Map<String, Object> summary(StaffOrderSummary row, UserDto user) {
        Map<String, Object> view = json.convertValue(row.summary(), MAP);
        view.put("customer", customer(row.userId(), user, false));
        view.put("materials", row.materials());
        view.put("next_actions", row.nextActions());
        return view;
    }

    Map<String, Object> full(StaffOrder staffOrder, UserDto user, List<MediaAssetDto> qcPhotos, List<NotificationDto> notifications) {
        Map<String, Object> view = json.convertValue(staffOrder.order(), MAP);
        view.put("customer", customer(staffOrder.userId(), user, true));
        view.put("next_actions", staffOrder.nextActions());
        view.put("qc_photos", qcPhotos.stream().map(p -> json.convertValue(p, MAP)).toList());
        view.put("notifications", notifications.stream().map(n -> json.convertValue(n, MAP)).toList());
        return view;
    }

    static Map<String, Object> customer(UUID userId, UserDto user, boolean withEmail) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", userId);
        customer.put("phone", user == null ? null : user.phone());
        customer.put("name", user == null ? null : user.name());
        if (withEmail) {
            customer.put("email", user == null ? null : user.email());
        }
        return customer;
    }
}
