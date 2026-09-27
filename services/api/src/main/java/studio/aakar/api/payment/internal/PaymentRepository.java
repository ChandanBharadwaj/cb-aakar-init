package studio.aakar.api.payment.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import studio.aakar.api.payment.PaymentStatus;

interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> lockById(@Param("id") UUID id);

    Optional<PaymentEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentEntity> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<PaymentEntity> findByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceSequence();
}
