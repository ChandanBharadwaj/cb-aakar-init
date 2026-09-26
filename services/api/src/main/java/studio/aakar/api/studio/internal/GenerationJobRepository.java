package studio.aakar.api.studio.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GenerationJobRepository extends JpaRepository<GenerationJobEntity, UUID> {

    /** Row lock so concurrent result appliers (callback thread, build thread) serialise per job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from GenerationJobEntity j where j.id = :id")
    Optional<GenerationJobEntity> lockById(@Param("id") UUID id);
}
