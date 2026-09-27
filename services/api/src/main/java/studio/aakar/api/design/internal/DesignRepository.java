package studio.aakar.api.design.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DesignRepository extends JpaRepository<DesignEntity, UUID> {

    /** Serialises version-number allocation per design. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesignEntity d where d.id = :id")
    Optional<DesignEntity> lockById(@Param("id") UUID id);

    /** Sign-in hand-over: the guest's designs become the user's. Returns the number moved. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DesignEntity d set d.ownerId = :userId, d.guestId = null, d.updatedAt = :now where d.guestId = :guestId and d.ownerId is null")
    int attachGuest(@Param("guestId") UUID guestId, @Param("userId") UUID userId, @Param("now") java.time.Instant now);
}
