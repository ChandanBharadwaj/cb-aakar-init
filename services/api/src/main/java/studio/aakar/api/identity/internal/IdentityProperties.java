package studio.aakar.api.identity.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.identity.*}.
 *
 * @param jwtSecret HS256 key material (at least 32 bytes); the default is a fixed dev string the production guard refuses
 * @param tokenTtl access-token lifetime (7 days for the local stack)
 * @param otp sign-in code rules
 */
@Validated
@ConfigurationProperties(prefix = "aakar.identity")
public record IdentityProperties(
        @NotBlank @Size(min = 32, message = "aakar.identity.jwt-secret must be at least 32 characters") String jwtSecret,
        @DefaultValue("7d") Duration tokenTtl,
        @DefaultValue Otp otp) {

    public record Otp(
            @DefaultValue("mock") String sender,
            @DefaultValue("true") boolean exposeDevCode,
            @DefaultValue("5m") Duration ttl,
            @DefaultValue("5") @Min(1) int maxRequestsPerWindow,
            @DefaultValue("15m") Duration window,
            @DefaultValue("5") @Min(1) int maxAttempts) {
    }
}
