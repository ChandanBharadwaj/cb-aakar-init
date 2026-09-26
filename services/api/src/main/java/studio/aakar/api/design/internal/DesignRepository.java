package studio.aakar.api.design.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DesignRepository extends JpaRepository<DesignEntity, UUID> {

    /** Serialises version-number allocation per design. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesignEntity d where d.id = :id")
    Optional<DesignEntity> lockById(@Param("id") UUID id);
}
