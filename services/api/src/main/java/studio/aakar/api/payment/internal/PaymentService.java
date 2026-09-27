package studio.aakar.api.payment.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.payment.GatewayPayment;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.payment.PaymentFailed;
import studio.aakar.api.payment.PaymentGateway;
import studio.aakar.api.payment.PaymentOutcome;
import studio.aakar.api.payment.PaymentRequest;
import studio.aakar.api.payment.PaymentStatus;
import studio.aakar.api.payment.PaymentSucceeded;
import studio.aakar.api.payment.Payments;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ClockConfig;
import studio.aakar.api.shared.ProblemCodes;

@Service
class PaymentService implements Payments {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentService(PaymentRepository payments, PaymentGateway gateway, ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentDto create(UUID orderId, String orderNumber, UUID userId, long amountPaise) {
        Instant now = clock.instant();
        List<PaymentEntity> open = payments.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.created, PaymentStatus.pending));
        open.forEach(p -> p.fail(null, null, now));
        if (!open.isEmpty()) {
            log.info("Order {}: {} abandoned payment attempt(s) marked failed", orderNumber, open.size());
        }
        UUID paymentId = UUID.randomUUID();
        GatewayPayment registered = gateway.create(new PaymentRequest(paymentId, orderId, orderNumber, userId, amountPaise, PaymentEntity.CURRENCY));
        PaymentEntity payment = payments.save(new PaymentEntity(paymentId, orderId, userId, gateway.name(), registered.gatewayRef(),
                amountPaise, registered.payUrl(), now));
        log.info("Payment {} created for order {} via {} ({} paise)", paymentId, orderNumber, gateway.name(), amountPaise);
        return payment.toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDto> find(UUID paymentId) {
        return payments.findById(paymentId).map(PaymentEntity::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDto> findForUser(UUID paymentId, UUID userId) {
        return payments.findByIdAndUserId(paymentId, userId).map(PaymentEntity::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDto> latestForOrder(UUID orderId) {
        return payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId).map(PaymentEntity::toDto);
    }

    @Override
    @Transactional
    public PaymentDto confirm(UUID paymentId, PaymentOutcome outcome, String method, String gatewayRef) {
        PaymentEntity payment = payments.lockById(paymentId).orElseThrow(() -> ApiProblemException.notFound("Payment", paymentId));
        if (payment.status().isFinal()) {
            throw ApiProblemException.conflict(ProblemCodes.PAYMENT_FINAL, "Payment already final",
                    "Payment " + paymentId + " is already " + payment.status() + " and cannot be confirmed again");
        }
        Instant now = clock.instant();
        if (outcome == PaymentOutcome.success) {
            String invoice = InvoiceNumbers.format(Year.now(clock.withZone(ClockConfig.STUDIO_ZONE)).getValue(), payments.nextInvoiceSequence());
            payment.succeed(method, gatewayRef, invoice, now);
            payments.flush();
            log.info("Payment {} succeeded ({}), invoice {}", paymentId, method, invoice);
            events.publishEvent(new PaymentSucceeded(payment.id(), payment.orderId(), payment.userId(), payment.amountPaise(), invoice, method));
        } else {
            payment.fail(method, gatewayRef, now);
            payments.flush();
            log.info("Payment {} failed ({})", paymentId, method);
            events.publishEvent(new PaymentFailed(payment.id(), payment.orderId(), payment.userId()));
        }
        return payment.toDto();
    }
}
