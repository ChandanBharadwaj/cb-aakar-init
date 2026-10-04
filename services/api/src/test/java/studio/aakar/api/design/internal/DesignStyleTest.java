package studio.aakar.api.design.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import studio.aakar.api.catalog.AdminExperienceDto;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.CreateDesignRequest;
import studio.aakar.api.design.DesignSource;
import studio.aakar.api.design.EditParamsRequest;
import studio.aakar.api.media.ContentTerms;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.studio.GenerationJobs;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

/**
 * Experience style on designs: a design started from an experience takes the experience's style as {@code spec.style} when its
 * template offers it ({@code style_variants}), else {@code none}, silently; a params edit keeps the parent's style; the feature
 * checks get the style (comic_pop has its own defaults). The descriptors are inline (the real fixtures gain {@code comic_pop}
 * only when the geometry service publishes it); the repositories, catalog and templates are mocks.
 */
class DesignStyleTest {

    static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    static final Instant NOW = Instant.parse("2026-10-03T09:00:00Z");
    static TemplateDescriptor badge;
    static TemplateDescriptor plain;

    private final DesignRepository designs = mock(DesignRepository.class);
    private final DesignVersionRepository versions = mock(DesignVersionRepository.class);
    private final Catalog catalog = mock(Catalog.class);
    private final Templates templates = mock(Templates.class);
    private final GenerationJobs jobs = mock(GenerationJobs.class);
    private final Identity guest = Identity.guest(UUID.randomUUID());
    private DesignService service;

    @BeforeAll
    static void descriptors() throws IOException {
        badge = JSON.readValue("""
                {"id": "test_badge", "version": 1, "family": "badge", "name": "Test badge", "params": {}, "materials": ["basic_white"],
                 "style_variants": ["comic_pop"], "features_supported": ["emboss_text", "motif"],
                 "anchors": [{"id": "face", "label": "Face", "kind": "surface", "projection": "planar", "accepts": ["emboss_text", "motif"]}]}
                """, TemplateDescriptor.class);
        plain = JSON.readValue("""
                {"id": "test_plain", "version": 1, "family": "badge", "name": "Test plain badge", "params": {}, "materials": ["basic_white"],
                 "features_supported": ["emboss_text"],
                 "anchors": [{"id": "face", "label": "Face", "kind": "surface", "projection": "planar", "accepts": ["emboss_text"]}]}
                """, TemplateDescriptor.class);
    }

    @BeforeEach
    void setUp() {
        when(designs.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(versions.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(jobs.start(any())).thenAnswer(inv -> UUID.randomUUID());
        when(templates.isLive(any())).thenReturn(true);
        when(templates.byId("test_badge")).thenReturn(Optional.of(badge));
        when(templates.byId("test_plain")).thenReturn(Optional.of(plain));
        when(catalog.material("basic_white")).thenReturn(Optional.of(
                new MaterialDto("basic_white", "Basic white", "PLA", 1.24, "matte", 300, false, Map.of(), true, 10, NOW)));
        when(catalog.adminExperience("comics")).thenReturn(Optional.of(experience("comics", "Katha", "comic_pop")));
        when(catalog.adminExperience("festive")).thenReturn(Optional.of(experience("festive", "Utsav", "jaipur_heritage")));
        service = new DesignService(designs, versions, catalog, templates, mock(Uploads.class), jobs, mock(VersionMapper.class), mock(ContentTerms.class));
    }

    @Test
    void anExperienceStyleTheTemplateOffersBecomesTheSpecStyle() {
        assertThat(created("test_badge", "comics").get("style")).isEqualTo("comic_pop");
        // the feature checks get the style, for its defaults (comic_pop: texts and motifs raised)
        verify(templates).validateFeatures(eq(badge), any(), anyList(), eq("comic_pop"));
    }

    @Test
    void aStyleTheTemplateDoesNotOfferFallsBackToNoneSilently() {
        assertThat(created("test_badge", "festive").get("style")).as("the badge offers comic_pop only").isEqualTo("none");
        assertThat(created("test_plain", "comics").get("style")).as("no style_variants at all").isEqualTo("none");
        assertThat(created("test_badge", null).get("style")).as("no experience").isEqualTo("none");
        verify(templates).validateFeatures(eq(plain), any(), anyList(), eq("none"));
    }

    @Test
    void anUnknownExperienceIsStillRefused() {
        assertThatThrownBy(() -> service.create(request("test_badge", "nowhere"), guest)).isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.code()).isEqualTo(ProblemCodes.UNKNOWN_EXPERIENCE));
    }

    @Test
    void aParamsEditKeepsTheParentsStyle() {
        Map<String, Object> parentSpec = created("test_badge", "comics");
        DesignVersionEntity parent = withId(new DesignVersionEntity(UUID.randomUUID(), 1, null, parentSpec, DesignSpecs.templateRef(badge), List.of(),
                DesignVersionEntity.CREATED_BY_USER, NOW));
        DesignEntity design = withId(new DesignEntity(DesignSource.remix, null, null, "comics", "Badge", guest, NOW));
        when(versions.findById(parent.id())).thenReturn(Optional.of(parent));
        when(designs.lockById(parent.designId())).thenReturn(Optional.of(design));
        when(versions.maxVersionNo(design.id())).thenReturn(1);

        service.editParams(parent.id(), new EditParamsRequest(Map.of(), null), guest);

        assertThat(lastSpec().get("style")).isEqualTo("comic_pop");
        assertThat(DesignSpecs.style(Map.of("spec_version", "1.0"))).as("a spec from before styles").isEqualTo("none");
        assertThat(DesignSpecs.style((Map<String, Object>) null)).isEqualTo("none");
    }

    @Test
    void styleDerivationIsAPureRule() {
        assertThat(DesignSpecs.style(badge, "comic_pop")).isEqualTo("comic_pop");
        assertThat(DesignSpecs.style(badge, "none")).isEqualTo("none");
        assertThat(DesignSpecs.style(badge, " ")).isEqualTo("none");
        assertThat(DesignSpecs.style(badge, "modern_zen")).isEqualTo("none");
        assertThat(DesignSpecs.style(plain, "comic_pop")).isEqualTo("none");
        assertThat(DesignSpecs.style(plain, null)).isEqualTo("none");
    }

    /** Creates a design from the template (remix path) with the experience, and returns the spec of its first version. */
    private Map<String, Object> created(String templateId, String experienceId) {
        service.create(request(templateId, experienceId), guest);
        return lastSpec();
    }

    private static CreateDesignRequest request(String templateId, String experienceId) {
        return new CreateDesignRequest(DesignSource.remix, null, null, templateId, Map.of(), "basic_white", List.of(), null, null, experienceId);
    }

    private Map<String, Object> lastSpec() {
        ArgumentCaptor<DesignVersionEntity> saved = ArgumentCaptor.forClass(DesignVersionEntity.class);
        verify(versions, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().get(saved.getAllValues().size() - 1).spec();
    }

    private static AdminExperienceDto experience(String id, String codename, String style) {
        return new AdminExperienceDto(id, codename, id, codename, null, null, "studio", null, style, List.of(), List.of(), List.of(), List.of(),
                List.of(), true, 10, NOW);
    }

    private static <T> T withId(T entity) {
        if (ReflectionTestUtils.getField(entity, "id") == null) {
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        }
        return entity;
    }
}
