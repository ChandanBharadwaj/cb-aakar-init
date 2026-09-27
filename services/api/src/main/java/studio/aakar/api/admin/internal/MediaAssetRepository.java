package studio.aakar.api.admin.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    List<MediaAssetEntity> findByOrderIdAndKindOrderByCreatedAtAsc(UUID orderId, String kind);
}
