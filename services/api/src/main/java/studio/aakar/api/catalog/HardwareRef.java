package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A bought-in part packed with a piece: {@code HardwareRef} in the storefront contract, {@code hardware[]} in
 * {@code template-family.v1.json}. {@code name} is filled by the API from {@code hardware_items} and never stored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HardwareRef(
        @NotBlank(message = "hardware.sku is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "hardware.sku must be snake_case starting with a letter")
        @Size(max = 40, message = "hardware.sku must be at most 40 characters") String sku,
        @NotNull(message = "hardware.qty is required") @Min(value = 1, message = "hardware.qty must be at least 1") Integer qty,
        String name) {

    public static HardwareRef of(String sku, int qty) {
        return new HardwareRef(sku, qty, null);
    }

    public HardwareRef named(String customerFacingName) {
        return new HardwareRef(sku, qty, customerFacingName);
    }
}
