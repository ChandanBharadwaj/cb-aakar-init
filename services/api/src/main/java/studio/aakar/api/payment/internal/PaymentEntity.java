package studio.aakar.api.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.payment.PaymentStatus;

@Entity
@Table(name = "payments")
class PaymentEntity {

    static final String CURRENCY = "INR";

    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String gateway;
    @Column(name = "gateway_ref")
    private String gatewayRef;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    private String method;
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
    @Column(nullable = false)
    private String currency;
    @Column(name = "pay_url")
    private String payUrl;
    @Column(name = "invoice_number")
    private String invoiceNumber;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    protected PaymentEntity() {
    }

    PaymentEntity(UUID id, UUID orderId, UUID userId, String gateway, String gatewayRef, long amountPaise, String payUrl, Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.gateway = gateway;
        this.gatewayRef = gatewayRef;
        this.status = PaymentStatus.created;
        this.amountPaise = amountPaise;
        this.currency = CURRENCY;
        this.payUrl = payUrl;
        this.createdAt = now;
    }

    UUID id() {
        return id;
    }

    UUID orderId() {
        return orderId;
    }

    UUID userId() {
        return userId;
    }

    String gateway() {
        return gateway;
    }

    PaymentStatus status() {
        return status;
    }

    long amountPaise() {
        return amountPaise;
    }

    void succeed(String method, String gatewayRef, String invoiceNumber, Instant now) {
        this.status = PaymentStatus.succeeded;
        this.method = method;
        if (gatewayRef != null && !gatewayRef.isBlank()) {
            this.gatewayRef = gatewayRef;
        }
        this.invoiceNumber = invoiceNumber;
        this.finishedAt = now;
    }

    void fail(String method, String gatewayRef, Instant now) {
        this.status = PaymentStatus.failed;
        this.method = method;
        if (gatewayRef != null && !gatewayRef.isBlank()) {
            this.gatewayRef = gatewayRef;
        }
        this.finishedAt = now;
    }

    PaymentDto toDto() {
        return new PaymentDto(id, orderId, gateway, gatewayRef, status, method, amountPaise, currency, payUrl, invoiceNumber, createdAt, finishedAt);
    }
}
