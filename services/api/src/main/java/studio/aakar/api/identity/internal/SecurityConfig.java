package studio.aakar.api.identity.internal;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.ProblemResponses;

/**
 * Stateless resource-server style chain. Public: catalog (items, shelves, materials), families, experiences and environments,
 * templates, designs (unguessable ids), versions, jobs, cart and uploads (guest or user; the services answer 401 without either),
 * auth, shipping serviceability, the geometry callback, media files, actuator and docs.
 * Signed-in only: profile, addresses, checkout, orders, payments. Unauthenticated calls to those answer
 * 401 {@code unauthenticated} as Problem Details. Unknown routes stay public so they 404 like before.
 * {@code /admin/api/**} never reaches this chain: the admin module registers its own, earlier-ordered chain
 * for staff tokens.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** After the admin module's staff chain (order 10), which matches {@code /admin/api/**} only. */
    static final int CUSTOMER_CHAIN_ORDER = 100;
    static final String[] AUTHENTICATED = {
        "/api/auth/me", "/api/auth/logout", "/api/me/**", "/api/checkout", "/api/orders/**", "/api/payments/**"
    };
    static final String[] PUBLIC = {
        "/api/catalog/**", "/api/families/**", "/api/experiences/**", "/api/environments/**", "/api/templates/**", "/api/designs/**",
        "/api/versions/**", "/api/jobs/**", "/api/cart/**", "/api/uploads/**", "/api/auth/**", "/api/shipping/**", "/internal/**", "/media/**",
        "/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error"
    };

    /**
     * Customers sign in with OTP and bearer tokens, never a password. Declaring an {@link AuthenticationManager}
     * keeps Boot from wiring its default in-memory user (and logging a generated password on every start).
     */
    @Bean
    AuthenticationManager noPasswordAuthentication() {
        return authentication -> {
            throw new BadCredentialsException("Password authentication is not offered; sign in with a phone OTP");
        };
    }

    @Bean
    @Order(CUSTOMER_CHAIN_ORDER)
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionService sessions, ProblemResponses problems,
            UrlBasedCorsConfigurationSource cors) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> problems.write(response, HttpStatus.UNAUTHORIZED,
                                ProblemCodes.UNAUTHENTICATED, "Unauthenticated", "Sign in to continue (send Authorization: Bearer <token>)"))
                        .accessDeniedHandler((request, response, ex) -> problems.write(response, HttpStatus.FORBIDDEN,
                                ProblemCodes.FORBIDDEN, "Forbidden", "You are not allowed to do that")))
                .authorizeHttpRequests(a -> a
                        // SSE emitters complete through an ASYNC re-dispatch that carries no security context; the
                        // original REQUEST dispatch was already authorised.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(AUTHENTICATED).authenticated()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(new IdentityFilter(sessions, problems), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
