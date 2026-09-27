package studio.aakar.api.admin;

import java.util.Optional;

/**
 * Staff account administration. There is no HTTP endpoint for it yet (the seed owner is created at startup);
 * operators and tests create further accounts through this API.
 */
public interface StaffAccounts {

    /** Creates an account with a bcrypt-hashed password; 409 {@code staff_exists} when the email is taken. */
    StaffDto create(String email, String name, StaffRole role, String password);

    Optional<StaffDto> byEmail(String email);
}
