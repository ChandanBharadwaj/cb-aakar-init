package studio.aakar.api.identity.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import studio.aakar.api.shared.Identity;

/** A signed-in customer in the Spring Security context: principal is the {@link Identity}, plus the session (jti). */
final class IdentityAuthentication extends AbstractAuthenticationToken {

    static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";

    private final Identity identity;
    private final UUID sessionId;

    IdentityAuthentication(Identity identity, UUID sessionId) {
        super(List.of(new SimpleGrantedAuthority(ROLE_CUSTOMER)));
        this.identity = identity;
        this.sessionId = sessionId;
        setAuthenticated(true);
    }

    Identity identity() {
        return identity;
    }

    UUID sessionId() {
        return sessionId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return identity;
    }

    @Override
    public String getName() {
        return identity.toString();
    }
}
