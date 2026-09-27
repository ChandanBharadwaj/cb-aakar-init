package studio.aakar.api.order.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderEventRepository extends JpaRepository<OrderEventEntity, Long> {

    List<OrderEventEntity> findByOrderIdAndSequenceGreaterThanOrderBySequenceAsc(UUID orderId, int sequence);

    @Query("select coalesce(max(e.sequence), 0) from OrderEventEntity e where e.orderId = :orderId")
    int maxSequence(@Param("orderId") UUID orderId);
}
