package studio.aakar.api.templates.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.FamilyLimits;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * The API's copy of the geometry service's feature checks ({@code features/validate.py}): every rule, the
 * Devanagari text length and the lithophane exemption. Real descriptors come from {@code fixtures/templates.json} (the
 * carriers take text and motifs since PR 3b); a plaque with a text-only edge, a lithophane window and a volume is built inline.
 */
class FeatureValidatorTest {

    static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    static final String UPLOAD = UUID.randomUUID().toString();
    static final FamilyLimits KEYCHAIN = new FamilyLimits("keychain", "carrier", 16, 30.0, 60.0);
    static final FamilyLimits RAW = new FamilyLimits("raw_print", "raw", null, 20.0, 240.0);

    static TemplateDescriptor keychain;
    static TemplateDescriptor raw;
    static TemplateDescriptor plaque;
    final FeatureValidator validator = new FeatureValidator();

    @BeforeAll
    static void loadDescriptors() throws IOException {
        try (InputStream in = FeatureValidatorTest.class.getResourceAsStream("/fixtures/templates.json")) {
            List<TemplateDescriptor> all = JSON.readValue(in, new TypeReference<>() { });
            keychain = all.stream().filter(d -> d.id().equals("keychain_tag")).findFirst().orElseThrow();
            raw = all.stream().filter(d -> d.id().equals("raw_print")).findFirst().orElseThrow();
        }
        // A plaque that takes text and motifs on its face, text only on its edge, a lithophane window and a plinth volume.
        plaque = JSON.readValue("""
                {"id": "test_plaque", "version": 1, "family": "nameplate", "name": "Test plaque",
                 "params": {}, "constraints": {"min_wall_mm": 1.2, "max_overhang_deg": 55, "bed_mm": [250, 250, 250]}, "materials": ["basic_white"],
                 "features_supported": ["emboss_text", "motif", "relief_image", "hero_mesh"],
                 "anchors": [
                   {"id": "face", "label": "Face", "kind": "surface", "projection": "planar", "size_mm": [120, 40], "accepts": ["emboss_text", "motif", "relief_image", "hero_mesh"], "max_relief_mm": 1.5},
                   {"id": "edge", "label": "Edge", "kind": "surface", "projection": "planar", "accepts": ["emboss_text"], "max_relief_mm": 0.8},
                   {"id": "window", "label": "Window", "kind": "surface", "projection": "planar", "accepts": ["relief_image"], "max_relief_mm": 1.0},
                   {"id": "plinth", "label": "Plinth", "kind": "volume", "projection": "planar", "bounds_mm": [60, 60, 80], "accepts": ["hero_mesh", "relief_image"]}
                 ]}
                """, TemplateDescriptor.class);
    }

    @Test
    void aPhotoReliefIsNormalisedWithTheContractDefaults() {
        List<Map<String, Object>> out = validator.validate(keychain, KEYCHAIN, List.of(relief("face")));

        assertThat(out).singleElement().satisfies(f -> {
            assertThat(f).containsExactly(Map.entry("type", "relief_image"), Map.entry("source", Map.of("upload_id", UPLOAD, "origin", "upload")),
                    Map.entry("anchor", "face"), Map.entry("mode", "emboss"), Map.entry("relief_mm", 0.6), Map.entry("fit", "contain"),
                    Map.entry("invert", false), Map.entry("cutout", "none"));
        });
        // both faces of the keychain take a photo
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(relief("face"), relief("back")))).hasSize(2);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of())).isEmpty();
        assertThat(validator.validate(keychain, KEYCHAIN, null)).isEmpty();
    }

    @Test
    void textMotifAndFormDefaultsFollowTheContract() {
        Map<String, Object> text = validator.validate(plaque, FamilyLimits.none("nameplate"), List.of(text("face", "Asha"))).get(0);
        assertThat(text).containsEntry("depth_mm", 1.2).containsEntry("projection", "planar").containsEntry("mode", "emboss")
                .doesNotContainKey("height_mm").doesNotContainKey("script");
        Map<String, Object> motif = validator.validate(plaque, FamilyLimits.none("nameplate"),
                List.of(Map.of("type", "motif", "motif_id", "lotus_border", "anchor", "face"))).get(0);
        assertThat(motif).containsEntry("scale", 1).containsEntry("depth_mm", 1.0).containsEntry("mode", "deboss");
        Map<String, Object> form = validator.validate(raw, RAW, List.of(hero(80))).get(0);
        assertThat(form).containsEntry("fit", "longest").containsEntry("longest_mm", 80).containsEntry("yaw_deg", 0)
                .containsEntry("orientation", "as_uploaded");
    }

    @Test
    void theTypeMustBeOneTheTemplateSupports() {
        // since lettering landed (PR 3b) the keychain takes a name on either face, but still no 3D form
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(text("face", "Asha"), text("back", "Ravi")))).hasSize(2);
        ApiProblemException problem = rejects(keychain, KEYCHAIN,
                List.of(Map.of("type", "hero_mesh", "source", Map.of("upload_id", UPLOAD), "anchor", "face")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("Saathi keychain tag can't carry your own 3D form (Roop) yet");
        assertThat(problem.properties()).containsEntry("unsupported", List.of("hero_mesh"));
        rejects(keychain, KEYCHAIN, List.of(Map.of("type", "sticker", "anchor", "face")), ProblemCodes.VALIDATION_FAILED);
    }

    @Test
    void theAnchorMustExistAndAcceptTheType() {
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(relief("top")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).contains("no such place").contains("Face, Back");
        // the keychain's back takes a photo or a name; motifs go on the face
        problem = rejects(keychain, KEYCHAIN, List.of(motif("back")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("The Back of Saathi keychain tag does not take motif (Buti)");
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(motif("face")))).hasSize(1);
        problem = rejects(plaque, FamilyLimits.none("nameplate"), List.of(relief("edge")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("The Edge of Test plaque does not take photo relief (Chhavi)");
        assertThat(problem.properties()).containsEntry("anchor", "edge").containsEntry("feature", 0);
    }

    @Test
    void formsGoOnVolumesAndEverythingElseOnSurfaces() {
        ApiProblemException problem = rejects(plaque, FamilyLimits.none("nameplate"),
                List.of(Map.of("type", "hero_mesh", "source", Map.of("upload_id", UPLOAD), "anchor", "face")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("The Face is a surface; your own 3D form (Roop) needs a place with room for it");
        problem = rejects(plaque, FamilyLimits.none("nameplate"), List.of(relief("plinth")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("The Plinth holds a 3D form; photo relief (Chhavi) needs a flat surface");
        assertThat(validator.validate(plaque, FamilyLimits.none("nameplate"),
                List.of(Map.of("type", "hero_mesh", "source", Map.of("upload_id", UPLOAD), "anchor", "plinth")))).hasSize(1);
    }

    @Test
    void onePhotoOrFormPerAnchor() {
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(relief("face"), relief("face")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Only one photo or 3D form can go on the Face");
        assertThat(problem.properties()).containsEntry("feature", 1);
        // text beside a photo on the same anchor is fine
        assertThat(validator.validate(plaque, FamilyLimits.none("nameplate"), List.of(relief("face"), text("face", "Asha")))).hasSize(2);
    }

    @Test
    void reliefAndEmbossDepthStayWithinTheAnchorCap() {
        Map<String, Object> deep = new LinkedHashMap<>(relief("face"));
        deep.put("relief_mm", 1.6);
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(deep), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("The photo relief (Chhavi) on the Face can be at most 1.5 mm deep; 1.6 mm was asked");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].relief_mm")).containsEntry("template_id", "keychain_tag");
        deep.put("relief_mm", 1.5);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(deep))).hasSize(1);
        // text without a depth uses the contract's 1.2 mm, deeper than the edge allows (as in the geometry service)
        problem = rejects(plaque, FamilyLimits.none("nameplate"), List.of(text("edge", "Asha")), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].depth_mm"));
        Map<String, Object> shallow = new LinkedHashMap<>(text("edge", "Asha"));
        shallow.put("depth_mm", 0.6);
        assertThat(validator.validate(plaque, FamilyLimits.none("nameplate"), List.of(shallow))).hasSize(1);
    }

    @Test
    void aLithophaneIsExemptFromTheReliefCap() {
        Map<String, Object> lithophane = new LinkedHashMap<>(relief("window"));
        lithophane.put("mode", "lithophane");
        lithophane.put("relief_mm", 2.5);
        assertThat(validator.validate(plaque, FamilyLimits.none("nameplate"), List.of(lithophane))).hasSize(1);
        lithophane.put("mode", "emboss");
        ApiProblemException problem = rejects(plaque, FamilyLimits.none("nameplate"), List.of(lithophane), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).contains("at most 1 mm deep; 2.5 mm was asked");
        // the contract's own range still holds for a lithophane
        lithophane.put("mode", "lithophane");
        lithophane.put("relief_mm", 3.5);
        rejects(plaque, FamilyLimits.none("nameplate"), List.of(lithophane), ProblemCodes.PARAM_OUT_OF_RANGE);
    }

    @Test
    void textLengthCountsWhatAReaderCounts() {
        assertThat(FeatureValidator.textLength("नमस्ते")).isEqualTo(4); // न म स ्(virama) त े(vowel sign): marks do not count
        assertThat(FeatureValidator.textLength("Asha")).isEqualTo(4);
        assertThat(FeatureValidator.textLength("é")).isEqualTo(1); // e + combining acute
        assertThat(FeatureValidator.textLength("क्‍ष")).isEqualTo(2); // a zero-width joiner (Cf) does not count
        assertThat(FeatureValidator.textLength("")).isZero();
        assertThat(FeatureValidator.textLength(null)).isZero();

        FamilyLimits four = new FamilyLimits("nameplate", "carrier", 4, null, null);
        assertThat(validator.validate(plaque, four, List.of(text("face", "नमस्ते")))).hasSize(1);
        ApiProblemException problem = rejects(plaque, four, List.of(text("face", "नमस्ते जी")), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("Text (Naam) on the Face can be at most 4 characters; 6 were given");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].text"));
        rejects(plaque, four, List.of(text("face", "Asha!")), ProblemCodes.PARAM_OUT_OF_RANGE);
        // the contract caps text at 40 code points whatever the family says
        rejects(plaque, FamilyLimits.none("nameplate"), List.of(text("face", "x".repeat(41))), ProblemCodes.PARAM_OUT_OF_RANGE);
        rejects(plaque, FamilyLimits.none("nameplate"), List.of(text("face", "   ")), ProblemCodes.VALIDATION_FAILED);
    }

    @Test
    void theRawFamilyNeedsExactlyOneFormSizedInsideTheEnvelope() {
        ApiProblemException problem = rejects(raw, RAW, List.of(), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Add your model file to print it as it is");
        problem = rejects(raw, RAW, List.of(hero(10)), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("Your model can be printed 20–240 mm on its longest side; 10 mm was asked");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].longest_mm"));
        rejects(raw, RAW, List.of(hero(241)), ProblemCodes.PARAM_OUT_OF_RANGE);
        rejects(raw, RAW, List.of(hero(300)), ProblemCodes.PARAM_OUT_OF_RANGE); // beyond the contract's 250 too
        assertThat(validator.validate(raw, RAW, List.of(hero(20)))).hasSize(1);
        assertThat(validator.validate(raw, RAW, List.of(hero(240)))).hasSize(1);

        Map<String, Object> contain = new LinkedHashMap<>(hero(80));
        contain.put("fit", "contain");
        problem = rejects(raw, RAW, List.of(contain), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Choose how long your model should be on its longest side (20–240 mm)");
        Map<String, Object> unsized = new LinkedHashMap<>(hero(80));
        unsized.remove("longest_mm");
        rejects(raw, RAW, List.of(unsized), ProblemCodes.VALIDATION_FAILED);
        // one volume anchor, so a second form is refused before the raw rule
        rejects(raw, RAW, List.of(hero(80), hero(90)), ProblemCodes.VALIDATION_FAILED);
        // a raw family without an envelope falls back to the contract's 5–250 mm
        assertThat(validator.validate(raw, new FamilyLimits("raw_print", "raw", null, null, null), List.of(hero(10)))).hasSize(1);
        // text or a photo never goes on the raw template
        rejects(raw, RAW, List.of(hero(80), relief("body")), ProblemCodes.UNSUPPORTED_FEATURE);
    }

    @Test
    void theShapeOfEachFeatureFollowsTheContract() {
        Map<String, Object> extra = new LinkedHashMap<>(relief("face"));
        extra.put("colour", "red");
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(extra), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.properties()).containsEntry("fields", List.of("colour"));
        rejects(keychain, KEYCHAIN, List.of(Map.of("type", "relief_image", "source", Map.of("upload_id", UPLOAD))), ProblemCodes.VALIDATION_FAILED);
        problem = rejects(keychain, KEYCHAIN, List.of(Map.of("type", "relief_image", "anchor", "face")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Add a photo for the photo relief (Chhavi)");
        rejects(keychain, KEYCHAIN, List.of(Map.of("type", "relief_image", "anchor", "face", "source", Map.of("upload_id", "not-a-uuid"))),
                ProblemCodes.VALIDATION_FAILED);
        rejects(keychain, KEYCHAIN, List.of(Map.of("type", "relief_image", "anchor", "face", "source", Map.of("upload_id", UPLOAD, "path", "/x"))),
                ProblemCodes.VALIDATION_FAILED);
        Map<String, Object> badMode = new LinkedHashMap<>(relief("face"));
        badMode.put("mode", "engrave");
        rejects(keychain, KEYCHAIN, List.of(badMode), ProblemCodes.VALIDATION_FAILED);
        Map<String, Object> tooDeep = new LinkedHashMap<>(relief("face"));
        tooDeep.put("relief_mm", 5);
        problem = rejects(keychain, KEYCHAIN, List.of(tooDeep), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("The depth of the photo relief (Chhavi) can be 0.2–3 mm; 5 mm was asked");
        Map<String, Object> notANumber = new LinkedHashMap<>(relief("face"));
        notANumber.put("relief_mm", "deep");
        rejects(keychain, KEYCHAIN, List.of(notANumber), ProblemCodes.VALIDATION_FAILED);
        Map<String, Object> cutout = new LinkedHashMap<>(relief("face"));
        cutout.put("cutout", "silhouette");
        rejects(keychain, KEYCHAIN, List.of(cutout), ProblemCodes.UNSUPPORTED_FEATURE);
        List<Object> notAnObject = new ArrayList<>();
        notAnObject.add("relief_image");
        assertThatThrownBy(() -> validator.validate(keychain, KEYCHAIN, castList(notAnObject))).isInstanceOf(ApiProblemException.class);
    }

    @Test
    void customerDetailsNeverUseCodeWords() {
        List<List<Map<String, Object>>> bad = List.of(
                List.of(motif("back")),
                List.of(text("face", "x".repeat(17))),
                List.of(relief("face"), relief("face")),
                List.of(Map.of("type", "hero_mesh", "source", Map.of("upload_id", UPLOAD), "anchor", "face")));
        for (List<Map<String, Object>> features : bad) {
            assertThatThrownBy(() -> validator.validate(keychain, KEYCHAIN, features)).isInstanceOfSatisfying(ApiProblemException.class,
                    e -> assertThat(e.getMessage()).doesNotContain("relief_image", "hero_mesh", "emboss_text", "_mm", "anchor"));
        }
    }

    private ApiProblemException rejects(TemplateDescriptor descriptor, FamilyLimits family, List<Map<String, Object>> features, String code) {
        try {
            validator.validate(descriptor, family, features);
        } catch (ApiProblemException e) {
            assertThat(e.code()).as(e.getMessage()).isEqualTo(code);
            assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(e.properties()).containsEntry("template_id", descriptor.id());
            return e;
        }
        throw new AssertionError("expected " + code + " for " + features);
    }

    private static Map<String, Object> relief(String anchor) {
        return Map.of("type", "relief_image", "source", Map.of("upload_id", UPLOAD), "anchor", anchor);
    }

    private static Map<String, Object> text(String anchor, String text) {
        return Map.of("type", "emboss_text", "text", text, "anchor", anchor);
    }

    private static Map<String, Object> motif(String anchor) {
        return Map.of("type", "motif", "motif_id", "lotus", "anchor", anchor);
    }

    private static Map<String, Object> hero(int longest) {
        Map<String, Object> hero = new LinkedHashMap<>();
        hero.put("type", "hero_mesh");
        hero.put("source", Map.of("upload_id", UPLOAD));
        hero.put("anchor", "body");
        hero.put("fit", "longest");
        hero.put("longest_mm", longest);
        return hero;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(List<Object> list) {
        return (List<Map<String, Object>>) (List<?>) list;
    }
}
