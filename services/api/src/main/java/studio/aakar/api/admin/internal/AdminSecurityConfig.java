package studio.aakar.api.admin.internal;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.ProblemResponses;

/**
 * The staff filter chain: matches {@code /admin/api/**} only and runs before the customer chain. Everything but
 * {@code POST /admin/api/auth/login} needs a staff token (401 {@code unauthenticated} as Problem Details).
 * Owner-only writes are enforced in the controllers via {@link StaffPrincipal#requireOwner()} (403 {@code forbidden}).
 */
@Configuration
class AdminSecurityConfig implements WebMvcConfigurer {

    static final int STAFF_CHAIN_ORDER = 10;
    static final String ADMIN_PATHS = "/admin/api/**";
    static final String LOGIN_PATH = "/admin/api/auth/login";

    static {
        // Resolved from the token by the staff filter, never a request parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(StaffPrincipal.class);
    }

    @Bean
    @Order(STAFF_CHAIN_ORDER)
    SecurityFilterChain adminSecurity(HttpSecurity http, StaffAuthService staff, ProblemResponses problems,
            UrlBasedCorsConfigurationSource cors) throws Exception {
        http.securityMatcher(ADMIN_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> problems.write(response, HttpStatus.UNAUTHORIZED,
                                ProblemCodes.UNAUTHENTICATED, "Unauthenticated", "Sign in with a staff account (POST " + LOGIN_PATH
                                        + ") and send Authorization: Bearer <token>"))
                        .accessDeniedHandler((request, response, ex) -> problems.write(response, HttpStatus.FORBIDDEN,
                                ProblemCodes.FORBIDDEN, "Forbidden", "You are not allowed to do that")))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, ADMIN_PATHS).permitAll()
                        .anyRequest().hasAuthority(StaffAuthentication.ROLE_STAFF))
                .addFilterBefore(new StaffAuthFilter(staff, problems), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new StaffArgumentResolver());
    }

    /** Lets admin controllers declare a {@link StaffPrincipal} parameter; the filter has already resolved it. */
    static final class StaffArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return StaffPrincipal.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            Object staff = webRequest.getAttribute(StaffPrincipal.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (staff instanceof StaffPrincipal principal) {
                return principal;
            }
            throw ApiProblemException.unauthenticated("Sign in with a staff account to use the management API");
        }
    }
}
