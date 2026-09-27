package studio.aakar.api.cart.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceInputs;

/** Pure rules behind cart lines: what is purchasable, when a snapshot needs re-pricing, and the specs line. */
final class CartPricing {

    private CartPricing() {
    }

    /** Ready, printability passed, and an estimate to price from. */
    static boolean purchasable(Optional<DesignVersionResponse> version) {
        return version.filter(v -> v.status() == VersionStatus.ready)
                .filter(v -> v.printability() != null && Boolean.TRUE.equals(v.printability().get("passed")))
                .flatMap(CartPricing::estimate)
                .isPresent();
    }

    static boolean needsReprice(String snapshotPolicyVersion, String activePolicyVersion) {
        return snapshotPolicyVersion == null || !snapshotPolicyVersion.equals(activePolicyVersion);
    }

    static Optional<PriceInputs.PrintEstimate> estimate(DesignVersionResponse version) {
        Map<String, Object> estimate = version.printEstimate();
        if (estimate == null) {
            return Optional.empty();
        }
        Object seconds = estimate.get("print_seconds");
        Object volume = estimate.get("extruded_volume_cm3");
        if (!(seconds instanceof Number s) || !(volume instanceof Number vol)) {
            return Optional.empty();
        }
        return Optional.of(new PriceInputs.PrintEstimate(s.intValue(), vol.doubleValue()));
    }

    static PriceInputs.Material inputs(MaterialDto material) {
        return new PriceInputs.Material(material.id(), material.densityGCm3(), material.finishClass(), material.ratePerGPaise());
    }

    /** e.g. {@code Terracotta Silk · 92 × 78 × 120 mm · 64 g}. Parts that are unknown are left out. */
    static String specsLine(String materialName, DesignVersionResponse version, PriceBreakdown price) {
        StringBuilder line = new StringBuilder();
        if (materialName != null && !materialName.isBlank()) {
            line.append(materialName);
        }
        bounds(version).ifPresent(b -> append(line, b));
        if (price != null && price.massG() > 0) {
            append(line, Math.round(price.massG()) + " g");
        }
        return line.toString();
    }

    static String thumbnailUrl(DesignVersionResponse version) {
        if (version == null || version.assets() == null) {
            return null;
        }
        Object thumb = version.assets().get("thumb");
        if (thumb instanceof Map<?, ?> m && m.get("url") instanceof String url) {
            return url;
        }
        return null;
    }

    static long lineTotal(PriceBreakdown unitPrice, int qty) {
        return unitPrice.subtotalPaise() * qty;
    }

    private static Optional<String> bounds(DesignVersionResponse version) {
        if (version == null || version.geometry() == null || !(version.geometry().get("bounds_mm") instanceof List<?> dims) || dims.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(dims.stream().map(CartPricing::mm).collect(Collectors.joining(" × ")) + " mm");
    }

    private static String mm(Object value) {
        if (!(value instanceof Number n)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static void append(StringBuilder line, String part) {
        if (!line.isEmpty()) {
            line.append(" · ");
        }
        line.append(part);
    }
}
