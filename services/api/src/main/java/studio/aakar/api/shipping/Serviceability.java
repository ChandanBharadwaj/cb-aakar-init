package studio.aakar.api.shipping;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Response of {@code GET /api/shipping/serviceability}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Serviceability(String pincode, boolean serviceable, String carrier, Integer etaDays, Boolean codAvailable) {

    public static Serviceability notServiceable(String pincode, String carrier) {
        return new Serviceability(pincode, false, carrier, null, null);
    }
}
