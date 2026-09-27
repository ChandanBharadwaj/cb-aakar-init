package studio.aakar.api.identity.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.identity.AddressDto;
import studio.aakar.api.identity.AddressInput;
import studio.aakar.api.shared.Identity;

@RestController
@RequestMapping("/api/me/addresses")
@Tag(name = "addresses")
@SecurityRequirement(name = "bearer")
class AddressController {

    private final AddressService addresses;

    AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    @Operation(summary = "The signed-in customer's addresses, default first")
    List<AddressDto> list(Identity identity) {
        return addresses.list(identity.requireUser());
    }

    @PostMapping
    @Operation(summary = "Add an address", description = "The first address becomes the default; `is_default: true` demotes the others.")
    ResponseEntity<AddressDto> create(@Valid @RequestBody AddressInput input, Identity identity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addresses.create(identity.requireUser(), input));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Replace an address", description = "404 when it is not one of the customer's addresses.")
    AddressDto update(@PathVariable UUID addressId, @Valid @RequestBody AddressInput input, Identity identity) {
        return addresses.update(identity.requireUser(), addressId, input);
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete an address", description = "Deleting the default promotes the oldest remaining address.")
    ResponseEntity<Void> delete(@PathVariable UUID addressId, Identity identity) {
        addresses.delete(identity.requireUser(), addressId);
        return ResponseEntity.noContent().build();
    }
}
