package studio.aakar.api.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code AddressInput} in the OpenAPI document. */
public record AddressInput(
        @Size(max = 30, message = "label must be at most 30 characters") String label,
        @NotBlank(message = "name is required") @Size(max = 80) String name,
        @NotBlank(message = "phone is required") @Size(max = 20) String phone,
        @NotBlank(message = "line1 is required") @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank(message = "city is required") @Size(max = 60) String city,
        @NotBlank(message = "state is required") @Size(max = 60) String state,
        @NotBlank(message = "pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be a 6-digit Indian PIN code") String pincode,
        Boolean isDefault) {

    public boolean wantsDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
