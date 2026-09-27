package studio.aakar.api.admin.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.ProblemResponses;

/**
 * Resolves the staff member behind a bearer token on {@code /admin/api/**}. A token that is not a live staff
 * token (malformed, expired, a customer token, an unknown account) is answered 401 {@code unauthenticated} at
 * once; a request without a token continues and meets the chain's {@code authenticated()} rule.
 */
final class StaffAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final StaffAuthService staff;
    private final ProblemResponses problems;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    StaffAuthFilter(StaffAuthService staff, ProblemResponses problems) {
        this.staff = staff;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            Optional<StaffAuthentication> authentication = staff.authenticate(authorization.substring(BEARER.length()).trim());
            if (authentication.isEmpty()) {
                problems.write(response, HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED, "Unauthenticated",
                        "The staff token is invalid or expired; sign in again at /admin/api/auth/login");
                return;
            }
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication.get());
            contextHolder.setContext(context);
            request.setAttribute(StaffPrincipal.REQUEST_ATTRIBUTE, authentication.get().staff());
        }
        chain.doFilter(request, response);
    }
}
