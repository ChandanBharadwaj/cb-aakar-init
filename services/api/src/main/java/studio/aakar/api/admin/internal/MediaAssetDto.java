package studio.aakar.api.admin.internal;

import java.time.Instant;
import java.util.UUID;

/** {@code MediaAsset} in the management contract. */
record MediaAssetDto(UUID id, String kind, UUID orderId, String url, String contentType, long bytes, String note, Instant createdAt) {
}
