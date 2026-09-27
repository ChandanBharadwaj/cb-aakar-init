package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.admin.StaffDto;

@RestController
@RequestMapping("/admin/api/auth")
@Tag(name = "admin · auth")
class StaffAuthController {

    private final StaffAuthService auth;

    StaffAuthController(StaffAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Staff sign-in", description = "Email + password against staff_accounts. Returns a 12-hour staff token "
            + "(typ=staff; never accepted by the customer API). 401 `unauthenticated` for a wrong pair.")
    StaffSession login(@Valid @RequestBody Login request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in staff member", security = @SecurityRequirement(name = "staffBearer"))
    StaffDto me(StaffPrincipal staff) {
        return auth.me(staff);
    }

    record Login(
            @NotBlank(message = "email is required") @Email(message = "email must be a valid address") String email,
            @NotBlank(message = "password is required") @Size(min = 8, max = 200, message = "password must be at least 8 characters") String password) {
    }
}
