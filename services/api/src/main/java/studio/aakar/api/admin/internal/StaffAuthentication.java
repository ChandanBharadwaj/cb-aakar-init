package studio.aakar.api.admin.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A signed-in staff member in the Spring Security context: {@code ROLE_STAFF}, plus {@code ROLE_OWNER} for owners. */
final class StaffAuthentication extends AbstractAuthenticationToken {

    static final String ROLE_STAFF = "ROLE_STAFF";
    static final String ROLE_OWNER = "ROLE_OWNER";

    private final StaffPrincipal staff;

    StaffAuthentication(StaffPrincipal staff) {
        super(authorities(staff));
        this.staff = staff;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(StaffPrincipal staff) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_STAFF));
        if (staff.isOwner()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_OWNER));
        }
        return authorities;
    }

    StaffPrincipal staff() {
        return staff;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return staff;
    }

    @Override
    public String getName() {
        return staff.email();
    }
}
