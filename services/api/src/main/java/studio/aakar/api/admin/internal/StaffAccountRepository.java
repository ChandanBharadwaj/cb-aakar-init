package studio.aakar.api.admin.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffAccountRepository extends JpaRepository<StaffAccountEntity, UUID> {

    Optional<StaffAccountEntity> findByEmailIgnoreCase(String email);
}
