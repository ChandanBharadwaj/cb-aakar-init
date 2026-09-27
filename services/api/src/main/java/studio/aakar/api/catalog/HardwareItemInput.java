package studio.aakar.api.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code AdminHardwareInput} in the management contract. */
public record HardwareItemInput(
        @NotBlank(message = "sku is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "sku must be snake_case starting with a letter")
        @Size(max = 40, message = "sku must be at most 40 characters") String sku,
        @NotBlank(message = "name is required") @Size(max = 120, message = "name must be at most 120 characters") String name,
        @NotNull(message = "unit_cost_paise is required") @Min(value = 0, message = "unit_cost_paise must be at least 0") Long unitCostPaise,
        @DecimalMin(value = "0", message = "weight_g must be at least 0") Double weightG,
        @Size(max = 120, message = "supplier must be at most 120 characters") String supplier,
        @Size(max = 500, message = "url must be at most 500 characters") String url,
        @Size(max = 200, message = "notes must be at most 200 characters") String notes,
        Boolean available) {

    public boolean availableOrDefault() {
        return available == null || available;
    }
}
