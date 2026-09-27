package studio.aakar.api.payment.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.payment.PaymentOutcome;
import studio.aakar.api.payment.Payments;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "payments")
@SecurityRequirement(name = "bearer")
class PaymentController {

    static final String DEFAULT_METHOD = "upi";

    private final Payments payments;

    PaymentController(Payments payments) {
        this.payments = payments;
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "A payment of the signed-in customer", description = "404 when it is not theirs.")
    PaymentDto payment(@PathVariable UUID paymentId, Identity identity) {
        return own(paymentId, identity);
    }

    @PostMapping("/{paymentId}/mock/complete")
    @Operation(summary = "Placeholder gateway page reports the outcome (mock gateway only)",
            description = "Runs the same confirmation path a gateway webhook would. 409 `payment_final` when the payment is not a "
                    + "mock payment or is already succeeded/failed. Success confirms and queues the order, empties the cart and "
                    + "records the confirmation message; failure leaves the order awaiting payment.")
    PaymentDto mockComplete(@PathVariable UUID paymentId, @Valid @RequestBody MockComplete request, Identity identity) {
        PaymentDto payment = own(paymentId, identity);
        if (!MockPaymentGateway.NAME.equals(payment.gateway())) {
            throw ApiProblemException.conflict(ProblemCodes.PAYMENT_FINAL, "Not a mock payment",
                    "Payment " + paymentId + " belongs to the " + payment.gateway() + " gateway, which confirms through its own webhook");
        }
        String method = request.method() == null || request.method().isBlank() ? DEFAULT_METHOD : request.method();
        return payments.confirm(paymentId, request.outcome(), method, payment.gatewayRef());
    }

    private PaymentDto own(UUID paymentId, Identity identity) {
        return payments.findForUser(paymentId, identity.requireUser()).orElseThrow(() -> ApiProblemException.notFound("Payment", paymentId));
    }

    record MockComplete(
            @NotNull(message = "outcome is required (success or failure)") PaymentOutcome outcome,
            @Pattern(regexp = "^(upi|card|netbanking)$", message = "method must be upi, card or netbanking") String method) {
    }
}
