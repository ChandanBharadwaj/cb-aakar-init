package studio.aakar.api.cart.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.cart.AddCartItemRequest;
import studio.aakar.api.cart.CartDto;
import studio.aakar.api.cart.Carts;
import studio.aakar.api.cart.UpdateCartItemRequest;
import studio.aakar.api.shared.Identity;

/** Guest or user; the identity comes from {@code Authorization} or {@code X-Aakar-Guest} (401 with neither). */
@RestController
@RequestMapping("/api/cart")
@Tag(name = "cart")
class CartController {

    private final Carts carts;

    CartController(Carts carts) {
        this.carts = carts;
    }

    @GetMapping
    @Operation(summary = "The current identity's cart (user or guest)",
            description = "Items are re-priced against the active pricing policy; an item whose snapshot changed carries `repriced: true`.")
    CartDto cart(Identity identity) {
        return carts.cart(identity);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    CartDto clear(Identity identity) {
        return carts.clear(identity);
    }

    @PostMapping("/items")
    @Operation(summary = "Add a design version in a finish",
            description = "409 `version_not_ready` while generating or failed, 409 `not_printable` when printability failed, "
                    + "422 `unknown_material`. The same version and finish twice adds up (max 20).")
    ResponseEntity<CartDto> add(@Valid @RequestBody AddCartItemRequest request, Identity identity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carts.add(identity, request));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Change quantity and/or finish of a line")
    CartDto update(@PathVariable UUID itemId, @Valid @RequestBody UpdateCartItemRequest request, Identity identity) {
        return carts.update(identity, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove a line")
    CartDto remove(@PathVariable UUID itemId, Identity identity) {
        return carts.remove(identity, itemId);
    }
}
