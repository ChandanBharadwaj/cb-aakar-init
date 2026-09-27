package studio.aakar.api.order.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderRepository extends JpaRepository<OrderEntity, UUID>, JpaSpecificationExecutor<OrderEntity> {

    /** Serialises status changes and event numbering per order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    List<OrderEntity> findByUserIdOrderByPlacedAtDesc(UUID userId);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    /** {@code [status, count]} rows for the statuses that have orders. */
    @Query("select o.status, count(o) from OrderEntity o group by o.status")
    List<Object[]> countByStatus();

    long countByPlacedAtGreaterThanEqual(Instant from);

    /** Revenue: totals of non-cancelled orders placed since {@code from} that have a succeeded payment. */
    @Query(value = """
            select cast(coalesce(sum(o.total_paise), 0) as bigint) from orders o
            where o.placed_at >= :from and o.status <> 'cancelled'
              and exists (select 1 from payments p where p.order_id = o.id and p.status = 'succeeded')
            """, nativeQuery = true)
    long paidRevenueSince(@Param("from") Instant from);
}
