package studio.aakar.api.media.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ContentReviewRepository extends JpaRepository<ContentReviewEntity, UUID> {

    Optional<ContentReviewEntity> findFirstByUploadIdOrderByCreatedAtDesc(UUID uploadId);

    List<ContentReviewEntity> findByUploadIdInOrderByCreatedAtAsc(Collection<UUID> uploadIds);

    /** Serialises decisions on one review (two reviewers clicking at once: the second gets 409). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContentReviewEntity r where r.id = :id")
    Optional<ContentReviewEntity> lockById(@Param("id") UUID id);
}
