package studio.aakar.api.identity.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.identity.SessionResponse;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.shared.Identity;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth")
class AuthController {

    private final OtpService otp;
    private final AuthService auth;

    AuthController(OtpService otp, AuthService auth) {
        this.otp = otp;
        this.auth = auth;
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request a sign-in code for a phone number",
            description = "6-digit code, valid 5 minutes, at most 5 requests per phone per 15 minutes (429 `otp_rate_limited`). "
                    + "With the mock sender in the local profile the code is returned as `dev_code`.")
    ResponseEntity<OtpService.OtpRequested> requestCode(@Valid @RequestBody OtpRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(otp.request(request.phone()));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify the code, sign in, attach guest designs and cart",
            description = "Creates the user on first sign-in. With `X-Aakar-Guest`, the guest's designs get the user as owner and the "
                    + "guest cart merges into the user's cart; `attached` reports the counts. 401 `otp_invalid` / `otp_expired`.")
    SessionResponse verify(@Valid @RequestBody OtpVerify request, Identity identity) {
        return auth.signIn(request.requestId(), request.code(), identity);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", security = @SecurityRequirement(name = "bearer"))
    UserDto me(Identity identity) {
        return auth.me(identity.requireUser());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update profile", security = @SecurityRequirement(name = "bearer"))
    UserDto update(@Valid @RequestBody ProfileUpdate request, Identity identity) {
        return auth.updateProfile(identity.requireUser(), request.name(), request.email());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session token", security = @SecurityRequirement(name = "bearer"))
    ResponseEntity<Void> logout(Authentication authentication, Identity identity) {
        identity.requireUser();
        if (authentication instanceof IdentityAuthentication ia) {
            auth.logout(ia.sessionId());
        }
        return ResponseEntity.noContent().build();
    }

    record OtpRequest(
            @NotBlank(message = "phone is required")
            @Pattern(regexp = "^\\+91[6-9][0-9]{9}$", message = "phone must be an Indian mobile in E.164 form, e.g. +919876543210")
            String phone) {
    }

    record OtpVerify(
            @NotNull(message = "request_id is required") UUID requestId,
            @NotBlank(message = "code is required") @Size(min = 4, max = 8, message = "code must be 4 to 8 characters") String code) {
    }

    record ProfileUpdate(
            @Size(max = 80, message = "name must be at most 80 characters") String name,
            @Email(message = "email must be a valid address") @Size(max = 254) String email) {
    }
}
