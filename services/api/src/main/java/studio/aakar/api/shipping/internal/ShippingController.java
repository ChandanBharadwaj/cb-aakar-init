package studio.aakar.api.shipping.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shipping.Serviceability;
import studio.aakar.api.shipping.ShippingCarrier;

@RestController
@RequestMapping("/api/shipping")
@Tag(name = "shipping")
@Validated
class ShippingController {

    private final ShippingCarrier carrier;

    ShippingController(ShippingCarrier carrier) {
        this.carrier = carrier;
    }

    @GetMapping("/serviceability")
    @Operation(summary = "Can we deliver to this pincode, and how fast",
            description = "The mock carrier serves every 6-digit pincode in 4 days except those starting with 9.")
    Serviceability serviceability(
            @RequestParam @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be a 6-digit Indian PIN code") String pincode) {
        return carrier.serviceability(pincode);
    }
}
