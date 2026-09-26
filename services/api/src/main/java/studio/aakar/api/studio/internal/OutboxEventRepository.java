package studio.aakar.api.studio.internal;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Modifying
    @Query("update OutboxEventEntity o set o.publishedAt = :at where o.id = :id and o.publishedAt is null")
    int markPublished(@Param("id") Long id, @Param("at") Instant at);
}
