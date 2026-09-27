package studio.aakar.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/** ADR-0013: mock adapters never start in production. */
class ProductionGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ProductionGuard.class);

    @Test
    void localProfileStartsWithEveryMock() {
        runner.withPropertyValues("aakar.profile=local").run(context -> assertThat(context).hasNotFailed());
        runner.run(context -> assertThat(context).hasNotFailed()); // no profile at all → local
    }

    @Test
    void productionRefusesTheDefaults() {
        runner.withPropertyValues("aakar.profile=production").run(context -> {
            assertThat(context).hasFailed();
            String message = rootMessage(context.getStartupFailure());
            assertThat(message).contains("aakar.profile=production refuses mock adapters")
                    .contains("aakar.identity.otp.sender=mock")
                    .contains("aakar.identity.otp.expose-dev-code=true")
                    .contains("aakar.payments.gateway=mock")
                    .contains("aakar.shipping.carrier=mock")
                    .contains("aakar.messaging.sender=log")
                    .contains("aakar.identity.jwt-secret=")
                    .contains("aakar.admin.seed-password=aakar-studio");
        });
    }

    @Test
    void productionNamesOnlyTheOffendingProperties() {
        runner.withPropertyValues("aakar.profile=PRODUCTION",
                "aakar.identity.otp.sender=msg91", "aakar.identity.otp.expose-dev-code=false", "aakar.payments.gateway=razorpay",
                "aakar.shipping.carrier=mock", "aakar.messaging.sender=whatsapp", "aakar.identity.jwt-secret=" + "x".repeat(40),
                "aakar.admin.seed-password=a-real-staff-password")
                .run(context -> {
                    assertThat(context).hasFailed();
                    String message = rootMessage(context.getStartupFailure());
                    assertThat(message).contains("aakar.shipping.carrier=mock")
                            .doesNotContain("otp.sender").doesNotContain("payments.gateway").doesNotContain("messaging").doesNotContain("jwt")
                            .doesNotContain("seed-password");
                });
    }

    @Test
    void productionWithRealAdaptersStarts() {
        runner.withPropertyValues("aakar.profile=production",
                "aakar.identity.otp.sender=msg91", "aakar.identity.otp.expose-dev-code=false", "aakar.payments.gateway=razorpay",
                "aakar.shipping.carrier=delhivery", "aakar.messaging.sender=whatsapp", "aakar.identity.jwt-secret=" + "s".repeat(48),
                "aakar.admin.seed-password=a-real-staff-password")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void violationsArePureOverAnEnvironment() {
        MockEnvironment env = new MockEnvironment().withProperty("aakar.profile", "production")
                .withProperty("aakar.payments.gateway", "razorpay");
        assertThat(ProductionGuard.violations(env)).containsExactly(
                "aakar.admin.seed-password=" + ProductionGuard.DEFAULT_ADMIN_SEED_PASSWORD,
                "aakar.identity.jwt-secret=" + ProductionGuard.DEV_JWT_SECRET,
                "aakar.identity.otp.expose-dev-code=true",
                "aakar.identity.otp.sender=mock",
                "aakar.messaging.sender=log",
                "aakar.shipping.carrier=mock");
        assertThat(ProductionGuard.violations(new MockEnvironment())).isEmpty();
    }

    private static String rootMessage(Throwable failure) {
        Throwable t = failure;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
