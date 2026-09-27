package studio.aakar.api.order.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** Serialises status changes and event numbering per order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    List<OrderEntity> findByUserIdOrderByPlacedAtDesc(UUID userId);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();
}
