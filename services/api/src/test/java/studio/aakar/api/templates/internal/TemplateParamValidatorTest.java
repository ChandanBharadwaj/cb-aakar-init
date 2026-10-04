package studio.aakar.api.templates.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.TemplateDescriptor;

class TemplateParamValidatorTest {

    static TemplateDescriptor jharokha;
    final TemplateParamValidator validator = new TemplateParamValidator();

    @BeforeAll
    static void loadDescriptor() throws IOException {
        ObjectMapper json = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        try (InputStream in = TemplateParamValidatorTest.class.getResourceAsStream("/fixtures/templates.json")) {
            List<TemplateDescriptor> all = json.readValue(in, new TypeReference<>() { });
            jharokha = all.get(0);
        }
    }

    @Test
    void descriptorRoundTripsFromJson() {
        assertThat(jharokha.id()).isEqualTo("jharokha_phone_stand");
        assertThat(jharokha.ref()).isEqualTo("jharokha_phone_stand@1");
        assertThat(jharokha.family()).isEqualTo("phone_stand");
        assertThat(jharokha.params()).containsKeys("width_mm", "depth_mm", "height_mm", "tilt_deg", "lip_height_mm", "wall_mm", "arch_cusps");
        assertThat(jharokha.params().get("arch_cusps").type()).isEqualTo("integer");
        assertThat(jharokha.params().get("width_mm").defaultValue()).isEqualTo(92);
        assertThat(jharokha.defaultParams()).containsEntry("wall_mm", 3.2).containsEntry("arch_cusps", 5);
        assertThat(jharokha.constraints().toSpecConstraints()).containsEntry("min_wall_mm", 1.2).containsEntry("bed_mm", List.of(250.0, 250.0, 250.0));
        assertThat(jharokha.materials()).hasSize(6).contains("terracotta_silk");
    }

    @Test
    void acceptsDefaultsAndInRangeValues() {
        assertThatCode(() -> validator.validate(jharokha, jharokha.defaultParams())).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(jharokha, Map.of("width_mm", 110, "wall_mm", 2.4, "arch_cusps", 7.0))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(jharokha, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void rejectsOutOfRangeWith422AndListsTheKeys() {
        assertThatThrownBy(() -> validator.validate(jharokha, Map.of("width_mm", 200)))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.code()).isEqualTo(ProblemCodes.PARAM_OUT_OF_RANGE);
                    assertThat(e.getMessage()).contains("width_mm").contains("70").contains("110");
                    assertThat(e.properties()).containsEntry("params", List.of("width_mm")).containsEntry("template_id", "jharokha_phone_stand");
                });
    }

    @Test
    void reportsEveryOffendingKeyAtOnce() {
        Map<String, Object> params = new HashMap<>();
        params.put("width_mm", 10);            // below min
        params.put("arch_cusps", 5.5);         // not integral
        params.put("tilt_deg", "steep");       // not a number
        params.put("colour", "red");           // unknown key
        params.put("height_mm", 120);          // fine

        assertThatThrownBy(() -> validator.validate(jharokha, params))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    @SuppressWarnings("unchecked")
                    List<String> keys = (List<String>) e.properties().get("params");
                    assertThat(keys).containsExactlyInAnyOrder("width_mm", "arch_cusps", "tilt_deg", "colour");
                });
    }

    @Test
    void checksBooleanAndEnumTypes() {
        TemplateDescriptor withEnum = new TemplateDescriptor("t", 1, "f", "T", null, null, Map.of(
                "tray", new TemplateDescriptor.Param("boolean", "Tray", null, "", true, null, null, null, null, null, null),
                "finish", new TemplateDescriptor.Param("enum", "Finish", null, "", "smooth", null, null, null, List.of("smooth", "fluted"), null, null)),
                List.of(), null, List.of("basic_white"), null, null, null, null);

        assertThatCode(() -> validator.validate(withEnum, Map.of("tray", false, "finish", "fluted"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(withEnum, Map.of("tray", "yes", "finish", "glossy")))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.properties().get("params")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).containsExactlyInAnyOrder("tray", "finish"));
    }
}
