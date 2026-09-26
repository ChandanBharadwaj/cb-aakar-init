package studio.aakar.api.shared;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON conventions from {@code packages/contracts}: snake_case property names and ISO-8601 instants.
 * Applies to request/response bodies, SSE data frames and AMQP messages alike.
 */
@Configuration
public class JsonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer aakarJsonConventions() {
        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
