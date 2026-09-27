package studio.aakar.api.identity;

import java.util.UUID;

/** {@code Address} in the OpenAPI document: {@code AddressInput} plus its id. */
public record AddressDto(
        UUID id,
        String label,
        String name,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        boolean isDefault) {
}
