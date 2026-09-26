package studio.aakar.api.studio.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JobEventRepository extends JpaRepository<JobEventEntity, Long> {

    List<JobEventEntity> findByJobIdAndSequenceGreaterThanOrderBySequenceAsc(UUID jobId, int sequence);

    boolean existsByJobIdAndSourceEventId(UUID jobId, UUID sourceEventId);

    @Query("select coalesce(max(e.sequence), 0) from JobEventEntity e where e.jobId = :jobId")
    int maxSequence(@Param("jobId") UUID jobId);
}
