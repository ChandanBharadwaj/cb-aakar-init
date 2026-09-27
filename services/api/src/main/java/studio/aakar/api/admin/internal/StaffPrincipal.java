package studio.aakar.api.admin.internal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import studio.aakar.api.admin.StaffDto;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * The staff member making the current request, resolved once by {@link StaffAuthFilter} and injectable into
 * admin controller methods. Also the {@code Staff} JSON shape ({@code id, email, name, role}).
 */
public record StaffPrincipal(UUID id, String email, String name, StaffRole role) {

    /** Request attribute under which the resolved principal is stored. */
    public static final String REQUEST_ATTRIBUTE = StaffPrincipal.class.getName();

    public boolean isOwner() {
        return role == StaffRole.owner;
    }

    /** Configuration writes (pricing, materials, catalog, templates) are owner-only: 403 {@code forbidden} otherwise. */
    public void requireOwner() {
        if (!isOwner()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Forbidden",
                    "Only the owner account can change configuration; " + email + " has the " + role + " role");
        }
    }

    public StaffDto toDto() {
        return new StaffDto(id, email, name, role);
    }
}
