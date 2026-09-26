package studio.aakar.api.studio.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import studio.aakar.api.studio.Envelope;

/**
 * RabbitMQ topology (profile {@code rabbit}): topic exchange {@code aakar.design}; the API consumes
 * results from the durable queue {@code api.design.results} bound to {@code design.progress},
 * {@code design.completed} and {@code design.failed}. The geometry worker declares its own
 * {@code design.generate} queue.
 */
@Configuration
@Profile("rabbit")
class RabbitConfig {

    static final String EXCHANGE = "aakar.design";
    static final String RESULTS_QUEUE = "api.design.results";
    static final String[] RESULT_ROUTING_KEYS = {Envelope.DESIGN_PROGRESS, Envelope.DESIGN_COMPLETED, Envelope.DESIGN_FAILED};

    @Bean
    Declarables aakarDesignTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        Queue results = QueueBuilder.durable(RESULTS_QUEUE).build();
        Binding[] bindings = new Binding[RESULT_ROUTING_KEYS.length];
        for (int i = 0; i < RESULT_ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(results).to(exchange).with(RESULT_ROUTING_KEYS[i]);
        }
        return new Declarables(exchange, results, bindings[0], bindings[1], bindings[2]);
    }

    /** Envelopes travel as JSON with the API's conventions (snake_case, ISO-8601). */
    @Bean
    MessageConverter amqpMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
