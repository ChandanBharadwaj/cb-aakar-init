package studio.aakar.api.admin;

import java.util.UUID;

/** {@code Staff} in the management contract. */
public record StaffDto(UUID id, String email, String name, StaffRole role) {
}
