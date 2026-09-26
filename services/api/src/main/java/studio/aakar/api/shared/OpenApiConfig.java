package studio.aakar.api.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc: describe the API and generate schemas with the same snake_case conventions the JSON uses. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI aakarOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Aakar API")
                .version("1.0.0-phase0")
                .description("""
                        Storefront API for Aakar, an AI-assisted 3D printing studio. Phase 0 covers the vertical slice: \
                        catalog + materials, designs from a template (Shop and Remix-lite paths), parameter edits, \
                        job progress over SSE, printability and price per material. Money is integer paise. \
                        Errors are RFC 9457 Problem Details with a stable `code`. \
                        The hand-written contract lives in `packages/contracts/openapi/aakar-api.v1.yaml`.""")
                .license(new License().name("Proprietary")));
    }

    /** Without this springdoc resolves schemas with its own mapper and renders camelCase property names. */
    @Bean
    ModelResolver snakeCaseModelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }
}
