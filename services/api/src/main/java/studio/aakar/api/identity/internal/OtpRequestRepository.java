package studio.aakar.api.identity.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OtpRequestRepository extends JpaRepository<OtpRequestEntity, UUID> {

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
