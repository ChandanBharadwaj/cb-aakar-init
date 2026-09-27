package studio.aakar.api.cart.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    List<CartItemEntity> findByCartIdOrderByAddedAtAsc(UUID cartId);

    Optional<CartItemEntity> findByIdAndCartId(UUID id, UUID cartId);

    Optional<CartItemEntity> findByCartIdAndVersionIdAndMaterialId(UUID cartId, UUID versionId, String materialId);

    void deleteByCartId(UUID cartId);
}
