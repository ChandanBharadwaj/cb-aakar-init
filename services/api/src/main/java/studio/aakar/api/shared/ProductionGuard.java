package studio.aakar.api.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * ADR-0013: mock adapters and the OTP dev code must never run in production. When {@code aakar.profile}
 * is {@code production} and any external provider is still the mock (or messaging only logs, or the OTP
 * code is exposed, or the JWT secret or the staff seed password is the dev default, or customer uploads skip the
 * content scanner), the context fails to start with a message naming every offending property.
 */
@Component
public class ProductionGuard implements SmartInitializingSingleton {

    public static final String PROFILE = "aakar.profile";
    public static final String DEV_JWT_SECRET = "aakar-local-dev-secret-do-not-use-in-production-0123456789";
    public static final String DEFAULT_ADMIN_SEED_PASSWORD = "aakar-studio";

    /** Property → the value that is not allowed in production. */
    static final Map<String, String> FORBIDDEN_IN_PRODUCTION = Map.of(
            "aakar.identity.otp.sender", "mock",
            "aakar.identity.otp.expose-dev-code", "true",
            "aakar.payments.gateway", "mock",
            "aakar.shipping.carrier", "mock",
            "aakar.messaging.sender", "log",
            "aakar.identity.jwt-secret", DEV_JWT_SECRET,
            "aakar.admin.seed-password", DEFAULT_ADMIN_SEED_PASSWORD,
            "aakar.uploads.scanner", "noop");

    private final Environment environment;

    public ProductionGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("aakar.profile=production refuses mock adapters and dev shortcuts: "
                    + String.join("; ", violations)
                    + ". Configure real providers (ADR-0013) or run with aakar.profile=local.");
        }
    }

    /** Every property whose effective value is forbidden while {@code aakar.profile=production}; empty otherwise. */
    public static List<String> violations(Environment environment) {
        String profile = environment.getProperty(PROFILE, "local");
        if (!AakarProperties.PRODUCTION.equalsIgnoreCase(profile)) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        FORBIDDEN_IN_PRODUCTION.keySet().stream().sorted().forEach(property -> {
            String forbidden = FORBIDDEN_IN_PRODUCTION.get(property);
            String value = environment.getProperty(property, defaultOf(property));
            if (value != null && value.trim().equalsIgnoreCase(forbidden)) {
                violations.add(property + "=" + value.trim());
            }
        });
        return violations;
    }

    /** What the application uses when the property is absent; the defaults are the mocks by design (except the upload scanner). */
    private static String defaultOf(String property) {
        return switch (property) {
            case "aakar.identity.jwt-secret" -> DEV_JWT_SECRET;
            case "aakar.admin.seed-password" -> DEFAULT_ADMIN_SEED_PASSWORD;
            case "aakar.uploads.scanner" -> "terms"; // the file-name terms scanner is the default, not a mock
            default -> FORBIDDEN_IN_PRODUCTION.get(property);
        };
    }
}
