package studio.aakar.api.admin.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShareCodeRepository extends JpaRepository<ShareCodeEntity, String> {

    Optional<ShareCodeEntity> findByOrderId(UUID orderId);
}
