package studio.aakar.api.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Stateless resource-server style chain. Public: catalog, templates, designs (unguessable ids), versions,
 * jobs, cart (guest or user), auth, shipping serviceability, the geometry callback, actuator and docs.
 * Signed-in only: profile, addresses, checkout, orders, payments. Unauthenticated calls to those answer
 * 401 {@code unauthenticated} as Problem Details. Unknown routes stay public so they 404 like before.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    static final String[] AUTHENTICATED = {
        "/api/auth/me", "/api/auth/logout", "/api/me/**", "/api/checkout", "/api/orders/**", "/api/payments/**"
    };
    static final String[] PUBLIC = {
        "/api/catalog/**", "/api/templates/**", "/api/designs/**", "/api/versions/**", "/api/jobs/**", "/api/cart/**",
        "/api/auth/**", "/api/shipping/**", "/internal/**", "/actuator/**", "/swagger-ui/**", "/swagger-ui.html",
        "/v3/api-docs/**", "/error"
    };

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionService sessions, ProblemResponses problems,
            CorsConfigurationSource cors) throws Exception {
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
                        .requestMatchers(AUTHENTICATED).authenticated()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(new IdentityFilter(sessions, problems), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
