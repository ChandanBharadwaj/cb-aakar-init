package studio.aakar.api.pricing.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PricingPolicyRepository extends JpaRepository<PricingPolicyEntity, UUID> {

    Optional<PricingPolicyEntity> findFirstByActiveTrueOrderByCreatedAtDesc();

    Optional<PricingPolicyEntity> findByVersion(String version);

    List<PricingPolicyEntity> findByActiveTrue();

    List<PricingPolicyEntity> findAllByOrderByCreatedAtDesc();
}
