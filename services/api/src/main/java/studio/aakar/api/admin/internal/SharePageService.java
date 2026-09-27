package studio.aakar.api.admin.internal;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderItemDto;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.Orders;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ClockConfig;

/** Resolves a packaging-card share code to the piece it was printed for (public, no customer data). */
@Service
class SharePageService {

    private final ShareCodeRepository codes;
    private final Orders orders;
    private final Designs designs;

    SharePageService(ShareCodeRepository codes, Orders orders, Designs designs) {
        this.codes = codes;
        this.orders = orders;
        this.designs = designs;
    }

    @Transactional(readOnly = true)
    public SharedPieceDto resolve(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        ShareCodeEntity share = codes.findById(code).orElseThrow(() -> ApiProblemException.notFound("Share code", code));
        OrderDto order = orders.findForStaff(share.orderId())
                .orElseThrow(() -> ApiProblemException.notFound("Share code", code)).order();
        OrderItemDto item = order.items().stream()
                .filter(i -> share.versionId().equals(i.versionId()))
                .findFirst()
                .orElseGet(() -> order.items().get(0));
        Optional<DesignVersionResponse> version = designs.findVersion(item.versionId());
        String templateId = version.map(v -> v.template() == null ? null : String.valueOf(v.template().get("id"))).orElse(null);
        String thumbnail = version.map(DesignVersionResponse::assets).map(a -> assetUrl(a, "thumb")).orElse(null);
        LocalDate printedAt = order.events().stream()
                .filter(e -> e.status() == OrderStatus.packed || e.status() == OrderStatus.shipped)
                .map(OrderEventDto::at)
                .findFirst()
                .orElse(order.placedAt())
                .atZone(ClockConfig.STUDIO_ZONE)
                .toLocalDate();
        return new SharedPieceDto(code, item.title(), item.specsLine(), item.materialId(), item.materialName(), item.designId(),
                item.versionId(), templateId, printedAt, AdminOrderService.STUDIO_CITY, thumbnail,
                ShareCodes.PATH + code, "/design/" + item.designId());
    }

    @SuppressWarnings("unchecked")
    private static String assetUrl(Map<String, Object> assets, String kind) {
        Object asset = assets.get(kind);
        if (asset instanceof Map<?, ?> map && map.get("url") instanceof String url) {
            return url;
        }
        return null;
    }
}
