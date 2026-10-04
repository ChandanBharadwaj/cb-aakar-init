package studio.aakar.api.media.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import studio.aakar.api.media.UploadStatus;

interface UploadRepository extends JpaRepository<UploadEntity, UUID> {

    List<UploadEntity> findAllByOrderByCreatedAtDesc(Pageable page);

    List<UploadEntity> findByStatusOrderByCreatedAtDesc(UploadStatus status, Pageable page);

    /** Sign-in hand-over: the guest's uploads become the user's. Returns the number moved. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UploadEntity u set u.ownerId = :userId, u.guestId = null where u.guestId = :guestId and u.ownerId is null")
    int attachGuest(@Param("guestId") UUID guestId, @Param("userId") UUID userId);
}
