package studio.aakar.api.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Resolves the request's {@link Identity} once: a bearer token becomes {@code user} (and the Spring
 * Security authentication), otherwise a well-formed {@code X-Aakar-Guest} header becomes {@code guest},
 * otherwise {@code anonymous}. A bearer token that is malformed, expired or revoked is answered with
 * 401 {@code unauthenticated} on every path so clients drop it instead of silently acting as guests.
 */
final class IdentityFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final SessionService sessions;
    private final ProblemResponses problems;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    IdentityFilter(SessionService sessions, ProblemResponses problems) {
        this.sessions = sessions;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Identity identity;
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            Optional<IdentityAuthentication> authentication = sessions.authenticate(authorization.substring(BEARER.length()).trim());
            if (authentication.isEmpty()) {
                problems.write(response, HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED, "Unauthenticated",
                        "The access token is invalid, expired or revoked; sign in again");
                return;
            }
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication.get());
            contextHolder.setContext(context);
            identity = authentication.get().identity();
        } else {
            String guest = request.getHeader(Identity.GUEST_HEADER);
            if (guest == null || guest.isBlank()) {
                identity = Identity.anonymous();
            } else {
                Optional<UUID> guestId = parseUuid(guest.trim());
                if (guestId.isEmpty()) {
                    problems.write(response, HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Validation failed",
                            Identity.GUEST_HEADER + " must be a UUID");
                    return;
                }
                identity = Identity.guest(guestId.get());
            }
        }
        request.setAttribute(Identity.REQUEST_ATTRIBUTE, identity);
        chain.doFilter(request, response);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
