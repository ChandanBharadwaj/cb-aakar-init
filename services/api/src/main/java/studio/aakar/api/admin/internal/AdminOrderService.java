package studio.aakar.api.admin.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.identity.Users;
import studio.aakar.api.media.MediaStore;
import studio.aakar.api.media.StoredMedia;
import studio.aakar.api.notification.NotificationDto;
import studio.aakar.api.notification.Notifications;
import studio.aakar.api.notification.OutboundMessage;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderItemDto;
import studio.aakar.api.order.OrderSearch;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.Orders;
import studio.aakar.api.order.StaffOrder;
import studio.aakar.api.order.StaffOrderSummary;
import studio.aakar.api.shared.AakarProperties;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ClockConfig;
import studio.aakar.api.shared.PageDto;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Fulfilment operations for staff: the queue, stage advancement with the customer messages that go with it,
 * the print pack, QC photos and the packaging card. Every write is audited.
 */
@Service
class AdminOrderService {

    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    static final Set<OrderStatus> PACKED_OR_LATER = EnumSet.of(OrderStatus.packed, OrderStatus.shipped, OrderStatus.delivered);
    static final String STUDIO_CITY = "Bengaluru";
    static final int NOTIFICATIONS_ON_ORDER = 50;
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final Orders orders;
    private final Users users;
    private final Notifications notifications;
    private final MediaStore media;
    private final MediaAssetRepository mediaAssets;
    private final ShareCodes shareCodes;
    private final PrintPack printPack;
    private final AdminOrderMapper mapper;
    private final AuditLog audit;
    private final String webUrl;
    private final Clock clock;

    AdminOrderService(Orders orders, Users users, Notifications notifications, MediaStore media, MediaAssetRepository mediaAssets,
            ShareCodes shareCodes, PrintPack printPack, AdminOrderMapper mapper, AuditLog audit, AakarProperties properties, Clock clock) {
        this.orders = orders;
        this.users = users;
        this.notifications = notifications;
        this.media = media;
        this.mediaAssets = mediaAssets;
        this.shareCodes = shareCodes;
        this.printPack = printPack;
        this.mapper = mapper;
        this.audit = audit;
        this.webUrl = properties.web().url();
        this.clock = clock;
    }

    PageDto<Map<String, Object>> search(String statusCsv, String query, int page, int size) {
        PageDto<StaffOrderSummary> result = orders.search(new OrderSearch(parseStatuses(statusCsv), query), page, size);
        Map<UUID, UserDto> customers = users.findAll(result.items().stream().map(StaffOrderSummary::userId).collect(Collectors.toSet()));
        return new PageDto<>(result.items().stream().map(row -> mapper.summary(row, customers.get(row.userId()))).toList(), result.page(),
                result.size(), result.total());
    }

    Map<String, Object> get(UUID orderId) {
        return view(require(orderId));
    }

    Map<String, Object> advance(UUID orderId, OrderStatus status, String message, Map<String, Object> detail, StaffPrincipal staff) {
        StaffOrder before = require(orderId);
        String line = message == null || message.isBlank() ? StageMessages.defaultFor(status) : message.trim();
        OrderEventDto event = orders.advance(orderId, status, line, detail);
        StaffOrder after = require(orderId);
        notifyCustomer(after, status, detail);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("status", status);
        change.put("message", event.message());
        change.put("detail", detail == null ? Map.of() : detail);
        change.put("sequence", event.sequence());
        audit.record(staff.email(), AuditLog.ORDER_ADVANCE, before.order().number(), Map.of("status", before.order().status()), change);
        return view(after);
    }

    byte[] printPack(UUID orderId) {
        return printPack.build(require(orderId).order());
    }

    MediaAssetDto addQcPhoto(UUID orderId, MultipartFile file, String note, StaffPrincipal staff) {
        OrderDto order = require(orderId).order();
        if (file == null || file.isEmpty()) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed", "A non-empty multipart part 'file' is required");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ApiProblemException(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCodes.PAYLOAD_TOO_LARGE, "Photo too large",
                    "QC photos must be 10 MB or smaller; this one is " + file.getSize() + " bytes");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiProblemException.validation("The uploaded file could not be read");
        }
        String contentType = file.getContentType() == null || file.getContentType().isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        StoredMedia stored = media.store("qc/" + order.number(), file.getOriginalFilename(), bytes, contentType);
        MediaAssetDto asset = mediaAssets.save(new MediaAssetEntity(MediaAssetEntity.KIND_QC_PHOTO, order.id(), stored.key(), stored.url(), contentType,
                bytes.length, note == null || note.isBlank() ? null : note.trim(), clock.instant())).toDto();
        audit.record(staff.email(), AuditLog.ORDER_QC_PHOTO, order.number(), null, asset);
        log.info("QC photo {} attached to order {} by {}", asset.id(), order.number(), staff.email());
        return asset;
    }

    /** 409 {@code order_not_packed} before {@code packed}; mints the order's share code on the first call. */
    byte[] packagingCard(UUID orderId) {
        OrderDto order = require(orderId).order();
        if (!PACKED_OR_LATER.contains(order.status())) {
            throw ApiProblemException.conflict(ProblemCodes.ORDER_NOT_PACKED, "Order not packed",
                    "Order " + order.number() + " is " + order.status() + "; the packaging card is generated once the order is packed");
        }
        if (order.items().isEmpty()) {
            throw new IllegalStateException("Order " + order.number() + " has no items");
        }
        OrderItemDto first = order.items().get(0);
        ShareCodeEntity code = shareCodes.mintFor(order.id(), first.designId(), first.versionId());
        String finish = order.items().stream().map(i -> i.materialName() == null ? i.materialId() : i.materialName())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().collect(Collectors.joining(", "));
        String printed = "Printed in " + STUDIO_CITY + " · " + CARD_DATE.format(clock.instant().atZone(ClockConfig.STUDIO_ZONE));
        return PackagingCard.render(new PackagingCard.Content(order.number(), order.title(), finish, printed, shareCodes.link(code.code())));
    }

    private Map<String, Object> view(StaffOrder staffOrder) {
        UUID orderId = staffOrder.order().id();
        List<MediaAssetDto> photos = mediaAssets.findByOrderIdAndKindOrderByCreatedAtAsc(orderId, MediaAssetEntity.KIND_QC_PHOTO).stream()
                .map(MediaAssetEntity::toDto).toList();
        List<NotificationDto> messages = notifications.page(orderId, 0, NOTIFICATIONS_ON_ORDER).items();
        return mapper.full(staffOrder, users.find(staffOrder.userId()).orElse(null), photos, messages);
    }

    private void notifyCustomer(StaffOrder staffOrder, OrderStatus status, Map<String, Object> detail) {
        String template = switch (status) {
            case printing -> OutboundMessage.PRINTING_TIMELAPSE;
            case shipped -> OutboundMessage.SHIPPED;
            case delivered -> OutboundMessage.DELIVERED;
            default -> null;
        };
        if (template == null) {
            return;
        }
        OrderDto order = staffOrder.order();
        Optional<UserDto> user = users.find(staffOrder.userId());
        String phone = user.map(UserDto::phone).orElseGet(() -> addressField(order.address(), "phone"));
        if (phone == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", order.id().toString());
        payload.put("order_number", order.number());
        payload.put("title", order.title());
        switch (status) {
            case printing -> {
                payload.put("tracking_url", webUrl + "/orders/" + order.id());
                if (detail != null) {
                    if (detail.get("layer") != null) {
                        payload.put("layer", detail.get("layer"));
                    }
                    if (detail.get("layers_total") != null) {
                        payload.put("layers_total", detail.get("layers_total"));
                    }
                }
            }
            case shipped -> {
                if (order.shipment() != null) {
                    payload.put("carrier", order.shipment().carrier());
                    if (order.shipment().awb() != null) {
                        payload.put("awb", order.shipment().awb());
                    }
                    if (order.shipment().eta() != null) {
                        payload.put("eta", order.shipment().eta().toString());
                    }
                }
            }
            default -> { }
        }
        String channel = order.notifyWhatsapp() ? OutboundMessage.WHATSAPP : OutboundMessage.SMS;
        notifications.send(staffOrder.userId(), new OutboundMessage(channel, template, phone, payload, order.id()));
    }

    private StaffOrder require(UUID orderId) {
        return orders.findForStaff(orderId).orElseThrow(() -> ApiProblemException.notFound("Order", orderId));
    }

    static Set<OrderStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<OrderStatus> statuses = EnumSet.noneOf(OrderStatus.class);
        for (String raw : csv.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                statuses.add(OrderStatus.valueOf(name.toLowerCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        "Unknown order status '" + name + "' in status filter");
            }
        }
        return statuses;
    }

    private static String addressField(Map<String, Object> address, String key) {
        Object value = address == null ? null : address.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
