package studio.aakar.api.studio.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import studio.aakar.api.studio.DesignCompletedPayload;
import studio.aakar.api.studio.DesignFailedPayload;
import studio.aakar.api.studio.DesignGeneratePayload;
import studio.aakar.api.studio.DesignProgressPayload;
import studio.aakar.api.studio.Envelope;
import studio.aakar.api.studio.GenerationRequest;
import studio.aakar.api.support.Contracts;

/** The RabbitMQ / callback message shapes ({@code packages/contracts/schemas/events}) without a broker. */
class EnvelopeMapperTest {

    static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    final EnvelopeMapper mapper = new EnvelopeMapper(JSON);
    final UUID jobId = UUID.randomUUID();
    final UUID designId = UUID.randomUUID();
    final UUID versionId = UUID.randomUUID();
    final Map<String, Object> spec = Map.of("spec_version", "1.0", "family", "phone_stand", "template", "jharokha_phone_stand@1",
            "params", Map.of("width_mm", 92), "features", List.of(), "style", "none", "material", "terracotta_silk");

    @Test
    void generateEnvelopeMatchesTheContract() throws Exception {
        DesignGeneratePayload payload = mapper.generatePayload(jobId, new GenerationRequest(designId, versionId, 1, null, spec),
                "http://localhost:8080/internal/jobs/" + jobId + "/callback");
        Envelope envelope = mapper.generateEnvelope(payload);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(envelope));

        assertThat(envelope.type()).isEqualTo("design.generate");
        assertThat(EnvelopeMapper.routingKey(envelope)).isEqualTo("design.generate");
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("event_id").asText()).isNotBlank();
        assertThat(json.get("job_id").asText()).isEqualTo(jobId.toString());
        assertThat(json.get("design_id").asText()).isEqualTo(designId.toString());
        assertThat(json.get("occurred_at").asText()).contains("T").endsWith("Z");
        assertThat(json.has("sequence")).isFalse();
        JsonNode body = json.get("payload");
        assertThat(body.get("job_id").asText()).isEqualTo(jobId.toString());
        assertThat(body.get("version_no").asInt()).isEqualTo(1);
        assertThat(body.has("parent_version_id")).isFalse();
        assertThat(body.get("spec").get("template").asText()).isEqualTo("jharokha_phone_stand@1");
        assertThat(body.get("outputs")).extracting(JsonNode::asText).containsExactly("glb", "3mf", "stl");
        assertThat(body.get("callback_url").asText()).endsWith("/internal/jobs/" + jobId + "/callback");
    }

    @Test
    void rabbitDispatcherDropsTheCallbackUrl() {
        DesignGeneratePayload payload = mapper.generatePayload(jobId, new GenerationRequest(designId, versionId, 2, versionId, spec), "http://api/cb");
        DesignGeneratePayload amqp = RabbitJobDispatcher.withoutCallback(payload);

        assertThat(amqp.callbackUrl()).isNull();
        assertThat(amqp.parentVersionId()).isEqualTo(versionId);
        assertThat(amqp.versionNo()).isEqualTo(2);
        assertThat(mapper.toMap(amqp)).doesNotContainKey("callback_url").containsKey("parent_version_id");
    }

    @Test
    void parsesProgressCompletedAndFailedPayloads() throws Exception {
        Envelope progress = JSON.readValue("""
                {"type":"design.progress","version":1,"event_id":"%s","job_id":"%s","design_id":"%s",
                 "occurred_at":"2026-09-26T10:00:00Z","sequence":3,"payload":{"stage":"sculpting","message":"Weaving your design","percent":45}}
                """.formatted(UUID.randomUUID(), jobId, designId), Envelope.class);
        DesignProgressPayload p = mapper.progress(progress);
        assertThat(progress.sequence()).isEqualTo(3);
        assertThat(progress.occurredAt()).isEqualTo(Instant.parse("2026-09-26T10:00:00Z"));
        assertThat(p.stage()).isEqualTo("sculpting");
        assertThat(p.percent()).isEqualTo(45);

        Envelope failed = Envelope.of(Envelope.DESIGN_FAILED, jobId, designId, null, Map.of("job_id", jobId.toString(),
                "design_id", designId.toString(), "code", "not_printable", "message", "Walls too thin", "detail", Map.of("thinnest_wall_mm", 0.9)));
        DesignFailedPayload f = mapper.failed(failed);
        assertThat(f.code()).isEqualTo("not_printable");
        assertThat(f.detail()).containsEntry("thinnest_wall_mm", 0.9);

        var example = Contracts.contracts("examples/design.completed.example.json");
        if (Files.exists(example)) {
            Map<String, Object> payload = JSON.convertValue(Contracts.readJson(example), new com.fasterxml.jackson.core.type.TypeReference<>() { });
            DesignCompletedPayload c = mapper.completed(Envelope.of(Envelope.DESIGN_COMPLETED, jobId, designId, null, payload));
            assertThat(c.template()).containsEntry("id", "jharokha_phone_stand").containsEntry("version", 1);
            assertThat(c.assets()).containsKeys("glb", "3mf", "stl");
            assertThat(c.geometry()).containsEntry("triangles", 4210);
            assertThat(c.printEstimate()).containsEntry("print_seconds", 13200);
            assertThat(c.karigarNote()).startsWith("A jharokha-arch phone stand");
            assertThat(c.buildMs()).isEqualTo(4300L);
        }
    }

    @Test
    void rabbitTopologyDeclaresExchangeQueueAndBindings() {
        Declarables declarables = new RabbitConfig().aakarDesignTopology();

        List<TopicExchange> exchanges = declarables.getDeclarablesByType(TopicExchange.class);
        assertThat(exchanges).singleElement().satisfies(x -> {
            assertThat(x.getName()).isEqualTo("aakar.design");
            assertThat(x.isDurable()).isTrue();
        });
        List<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        assertThat(queues).singleElement().satisfies(q -> {
            assertThat(q.getName()).isEqualTo("api.design.results");
            assertThat(q.isDurable()).isTrue();
        });
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .allSatisfy(b -> {
                    assertThat(b.getExchange()).isEqualTo("aakar.design");
                    assertThat(b.getDestination()).isEqualTo("api.design.results");
                })
                .extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrder("design.progress", "design.completed", "design.failed");
    }
}
