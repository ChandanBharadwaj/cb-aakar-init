package studio.aakar.api.admin.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aakar.admin.*}.
 *
 * @param seedPassword password of the seed owner {@code studio@aakar.local}, hashed on first start; the production
 *        guard refuses the default {@code aakar-studio}
 * @param tokenTtl staff token lifetime (12 hours)
 */
@Validated
@ConfigurationProperties(prefix = "aakar.admin")
public record AdminProperties(
        @DefaultValue("aakar-studio") @NotBlank @Size(min = 8, message = "aakar.admin.seed-password must be at least 8 characters") String seedPassword,
        @DefaultValue("12h") Duration tokenTtl) {
}
