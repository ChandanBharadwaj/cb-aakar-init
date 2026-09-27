package studio.aakar.api.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

    List<AddressEntity> findByUserIdOrderByIsDefaultDescCreatedAtAsc(UUID userId);

    Optional<AddressEntity> findByIdAndUserId(UUID id, UUID userId);
}
