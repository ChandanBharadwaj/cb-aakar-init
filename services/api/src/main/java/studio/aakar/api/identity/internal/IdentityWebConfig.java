package studio.aakar.api.identity.internal;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import studio.aakar.api.shared.Identity;

/** Lets any controller declare an {@link Identity} parameter; the filter has already resolved it. */
@Configuration
class IdentityWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new IdentityArgumentResolver());
    }

    static final class IdentityArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Identity.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            Object identity = webRequest.getAttribute(Identity.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            return identity instanceof Identity i ? i : Identity.anonymous();
        }
    }
}
