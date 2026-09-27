package studio.aakar.api.identity;

import java.time.Instant;
import java.util.UUID;

/** {@code User} in the OpenAPI document. */
public record UserDto(UUID id, String phone, String name, String email, Instant createdAt) {
}
