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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.FamilyLimits;
import studio.aakar.api.templates.MotifDto;
import studio.aakar.api.templates.Motifs;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * The API's copy of the geometry service's feature checks ({@code features/validate.py}, {@code emboss_text.py},
 * {@code motif.py}): every rule, the Devanagari text length, the lithophane exemption, what one anchor holds (one photo or
 * form, one text, one motif, never a photo beside a text or a motif), flat lettering, letter height, the launch scripts and
 * the bundled lettering, and motifs from the library within their scale. Real descriptors come from
 * {@code fixtures/templates.json} (the carriers take text and motifs since PR 3b); a plaque with a text-only edge, a
 * lithophane window and a volume, and a mug with a curved wrap, are built inline. The motif library is a stand-in with the
 * real ids, labels and {@code min_scale}s ({@code MotifLibraryTest} and {@code MotifsIntegrationTest} read the files).
 */
class FeatureValidatorTest {

    static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    static final String UPLOAD = UUID.randomUUID().toString();
    static final FamilyLimits KEYCHAIN = new FamilyLimits("keychain", "carrier", 16, 30.0, 60.0);
    static final FamilyLimits RAW = new FamilyLimits("raw_print", "raw", null, 20.0, 240.0);
    static final FamilyLimits NAMEPLATE = FamilyLimits.none("nameplate");
    static final Motifs LIBRARY = new StubMotifs(true, List.of(
            new MotifDto("paisley", "Paisley", List.of("textile"), 0.3, "http://localhost:8080/api/motifs/paisley.svg"),
            new MotifDto("lotus", "Lotus", List.of("flower"), 0.25, "http://localhost:8080/api/motifs/lotus.svg"),
            new MotifDto("star_rangoli", "Rangoli star", List.of("rangoli"), 0.3, "http://localhost:8080/api/motifs/star_rangoli.svg")));

    static TemplateDescriptor keychain;
    static TemplateDescriptor raw;
    static TemplateDescriptor nameplate;
    static TemplateDescriptor plaque;
    static TemplateDescriptor mug;
    final FeatureValidator validator = new FeatureValidator(LIBRARY);

    @BeforeAll
    static void loadDescriptors() throws IOException {
        try (InputStream in = FeatureValidatorTest.class.getResourceAsStream("/fixtures/templates.json")) {
            List<TemplateDescriptor> all = JSON.readValue(in, new TypeReference<>() { });
            keychain = all.stream().filter(d -> d.id().equals("keychain_tag")).findFirst().orElseThrow();
            raw = all.stream().filter(d -> d.id().equals("raw_print")).findFirst().orElseThrow();
            nameplate = all.stream().filter(d -> d.id().equals("desk_nameplate")).findFirst().orElseThrow();
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
        // No template is curved yet; this one is, to show that lettering and motifs are not wrapped around anything.
        mug = JSON.readValue("""
                {"id": "test_mug", "version": 1, "family": "mug", "name": "Test mug", "params": {}, "materials": ["basic_white"],
                 "features_supported": ["emboss_text", "motif"],
                 "anchors": [{"id": "wrap", "label": "Wrap", "kind": "surface", "projection": "cylindrical", "size_mm": [200, 80],
                              "accepts": ["emboss_text", "motif"], "max_relief_mm": 1.5}]}
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
                List.of(Map.of("type", "motif", "motif_id", "lotus", "anchor", "face"))).get(0);
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
        // a text on the other face is fine (beside the photo it would be crowded, see below)
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(relief("face"), text("back", "Asha")))).hasSize(2);
    }

    @Test
    void anAnchorTakesOneTextAndOneMotifSideBySide() {
        // a text and a motif share the face (the motif first, the text beside it); another text goes on the back
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(text("face", "Asha"), motif("face"), text("back", "Ravi")))).hasSize(3);
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(text("face", "Asha"), text("face", "Ravi")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Only one text (Naam) can go on the Face; put the other one on another spot");
        assertThat(problem.properties()).containsEntry("anchor", "face").containsEntry("feature", 1);
        problem = rejects(keychain, KEYCHAIN, List.of(motif("face"), text("face", "Asha"), motif("face", "paisley")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Only one motif (Buti) can go on the Face; put the other one on another spot");
        assertThat(problem.properties()).containsEntry("feature", 2);
        problem = rejects(keychain, KEYCHAIN, List.of(text("back", "Asha"), text("back", "Ravi")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Only one text (Naam) can go on the Back; put the other one on another spot");
    }

    @Test
    void aPhotoFillsItsSpotSoATextOrAMotifBesideItIsCrowded() {
        String crowdedByText = "The Face is too crowded for a photo relief (Chhavi) and text (Naam) together; put the text (Naam) on another spot";
        String crowdedByMotif = "The Face is too crowded for a photo relief (Chhavi) and a motif (Buti) together; put the motif (Buti) on another spot";
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(relief("face"), text("face", "Asha")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo(crowdedByText);
        assertThat(problem.properties()).containsEntry("anchor", "face").containsEntry("feature", 1);
        // whichever came first
        assertThat(rejects(keychain, KEYCHAIN, List.of(text("face", "Asha"), relief("face")), ProblemCodes.VALIDATION_FAILED).getMessage())
                .isEqualTo(crowdedByText);
        assertThat(rejects(keychain, KEYCHAIN, List.of(relief("face"), motif("face")), ProblemCodes.VALIDATION_FAILED).getMessage())
                .isEqualTo(crowdedByMotif);
        assertThat(rejects(keychain, KEYCHAIN, List.of(motif("face"), relief("face")), ProblemCodes.VALIDATION_FAILED).getMessage())
                .isEqualTo(crowdedByMotif);
        // a photo joining a text and a motif names the first of them
        assertThat(rejects(keychain, KEYCHAIN, List.of(text("face", "Asha"), motif("face"), relief("face")), ProblemCodes.VALIDATION_FAILED)
                .properties()).containsEntry("feature", 2);
        // the plaque's face takes all three kinds, but still not together
        rejects(plaque, NAMEPLATE, List.of(relief("face"), text("face", "Asha")), ProblemCodes.VALIDATION_FAILED);
    }

    @Test
    void textsAndMotifsAreSetFlatForNow() {
        String curvedText = "Text (Naam) that wraps around a curved surface is not available yet; every spot today is flat";
        Map<String, Object> curved = new LinkedHashMap<>(text("back", "Asha"));
        for (String projection : List.of("cylindrical", "conformal")) {
            curved.put("projection", projection);
            ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(curved), ProblemCodes.UNSUPPORTED_FEATURE);
            assertThat(problem.getMessage()).isEqualTo(curvedText);
            assertThat(problem.properties()).containsEntry("field", "features[0].projection").containsEntry("anchor", "back");
        }
        curved.put("projection", "planar");
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(curved))).hasSize(1);
        // nor on a curved anchor, whatever the text asks
        ApiProblemException problem = rejects(mug, FamilyLimits.none("mug"), List.of(text("wrap", "Asha")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo(curvedText);
        assertThat(problem.properties()).containsEntry("anchor", "wrap").doesNotContainKey("field");
        problem = rejects(mug, FamilyLimits.none("mug"), List.of(motif("wrap")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("A motif (Buti) that wraps around a curved surface is not available yet; every spot today is flat");
        // the contract's own values still come first
        curved.put("projection", "spherical");
        rejects(keychain, KEYCHAIN, List.of(curved), ProblemCodes.VALIDATION_FAILED);
    }

    @Test
    void lettersStayWithinTheAnchorsTallestLetters() {
        Map<String, Object> tall = new LinkedHashMap<>(text("base_front", "Asha Rao"));
        tall.put("height_mm", 10);
        ApiProblemException problem = rejects(nameplate, NAMEPLATE, List.of(tall), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("Text (Naam) on the Front of the foot can be at most 8 mm tall; 10 mm was asked");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].height_mm")).containsEntry("feature", 0);
        tall.put("height_mm", 8);
        assertThat(validator.validate(nameplate, NAMEPLATE, List.of(tall))).hasSize(1);
        tall.put("anchor", "face");
        tall.put("height_mm", 40.5);
        problem = rejects(nameplate, NAMEPLATE, List.of(tall), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("Text (Naam) on the Face can be at most 36 mm tall; 40.5 mm was asked");
        tall.put("height_mm", 36);
        assertThat(validator.validate(nameplate, NAMEPLATE, List.of(tall))).hasSize(1);
        // an anchor without max_text_height_mm leaves the contract's 4–60 mm (the geometry service fits the letters to the spot)
        Map<String, Object> free = new LinkedHashMap<>(text("face", "Asha"));
        free.put("height_mm", 20);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(free))).hasSize(1);
        free.put("height_mm", 61);
        problem = rejects(keychain, KEYCHAIN, List.of(free), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("The letter height of the text (Naam) can be 4–60 mm; 61 mm was asked");
    }

    @Test
    void motifsComeFromTheLibraryAtAScaleThatPrints() {
        ApiProblemException problem = rejects(keychain, KEYCHAIN, List.of(motif("face", "peacock")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("We don't have a motif (Buti) called “peacock”; choose one from the motif library");
        assertThat(problem.properties()).containsEntry("field", "features[0].motif_id").containsEntry("feature", 0);
        // a malformed id never reaches the library
        problem = rejects(keychain, KEYCHAIN, List.of(motif("face", "Peacock!")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("That motif isn't one we know");

        Map<String, Object> small = new LinkedHashMap<>(motif("face", "paisley"));
        small.put("scale", 0.25);
        problem = rejects(keychain, KEYCHAIN, List.of(small), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("The Paisley motif (Buti) can't be printed smaller than scale 0.3; choose a larger scale");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].scale"));
        small.put("scale", 0.3);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(small))).singleElement().satisfies(f -> assertThat(f).containsEntry("scale", 0.3));
        small.put("scale", 1);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(small))).hasSize(1);
        // each motif has its own floor: the lotus prints down to 0.25, the rangoli star (a label, not the id) to 0.3
        Map<String, Object> lotus = new LinkedHashMap<>(motif("face", "lotus"));
        lotus.put("scale", 0.25);
        assertThat(validator.validate(keychain, KEYCHAIN, List.of(lotus))).hasSize(1);
        Map<String, Object> star = new LinkedHashMap<>(motif("face", "star_rangoli"));
        star.put("scale", 0.2);
        assertThat(rejects(keychain, KEYCHAIN, List.of(star), ProblemCodes.PARAM_OUT_OF_RANGE).getMessage())
                .isEqualTo("The Rangoli star motif (Buti) can't be printed smaller than scale 0.3; choose a larger scale");
        // scale 1 fills the spot: more would run past its edge, and under 0.2 is outside the contract (never clamped)
        small.put("scale", 1.5);
        problem = rejects(keychain, KEYCHAIN, List.of(small), ProblemCodes.PARAM_OUT_OF_RANGE);
        assertThat(problem.getMessage()).isEqualTo("The scale of the motif (Buti) can be 0.2–1; 1.5 was asked");
        assertThat(problem.properties()).containsEntry("params", List.of("features[0].scale"));
        small.put("scale", 0.1);
        rejects(keychain, KEYCHAIN, List.of(small), ProblemCodes.PARAM_OUT_OF_RANGE);
    }

    @Test
    void withoutTheLibraryMotifIdsAndScalesAreLeftToTheGeometryService() {
        FeatureValidator blind = new FeatureValidator(new StubMotifs(false, List.of()));
        Map<String, Object> unknown = new LinkedHashMap<>(motif("face", "peacock"));
        unknown.put("scale", 0.2);
        assertThat(blind.validate(keychain, KEYCHAIN, List.of(unknown))).hasSize(1);
        // the contract and the anchor still apply
        unknown.put("scale", 2);
        assertThatThrownBy(() -> blind.validate(keychain, KEYCHAIN, List.of(unknown))).isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.code()).isEqualTo(ProblemCodes.PARAM_OUT_OF_RANGE));
        assertThatThrownBy(() -> blind.validate(keychain, KEYCHAIN, List.of(motif("back")))).isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.code()).isEqualTo(ProblemCodes.UNSUPPORTED_FEATURE));
    }

    @Test
    void aTextIsWrittenInOneLaunchScriptInTheBundledLettering() {
        for (String word : List.of("Asha", "2024", "José", "नमस्ते दुनिया", "నమస్తే", "வணக்கம்", "ನಮಸ್ಕಾರ", "নমস্কার", "નમસ્તે", "राम।", "Asha & Ravi")) {
            assertThat(validator.validate(plaque, NAMEPLATE, List.of(text("face", word)))).as(word).hasSize(1);
        }
        // naming the right script is fine, and so is naming the bundled lettering (case, spaces and punctuation aside)
        assertThat(validator.validate(plaque, NAMEPLATE, List.of(lettering("नमस्ते", "devanagari", "Noto Sans Devanagari")))).hasSize(1);
        assertThat(validator.validate(plaque, NAMEPLATE, List.of(lettering("नमस्ते", null, "")))).hasSize(1);
        assertThat(validator.validate(plaque, NAMEPLATE, List.of(lettering("नमस्ते", null, "Noto Sans Bold")))).hasSize(1);
        for (String font : List.of("Noto Sans", "noto_sans", "Noto Sans Bold", "NotoSans-Bold")) {
            assertThat(validator.validate(plaque, NAMEPLATE, List.of(lettering("Asha", "latin", font)))).as(font).hasSize(1);
        }

        ApiProblemException problem = rejects(plaque, NAMEPLATE, List.of(text("face", "Asha आशा")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Please write the text (Naam) in one script: this mixes Latin and Devanagari letters. "
                + "Each can go on its own spot");
        assertThat(problem.properties()).containsEntry("field", "features[0].text");
        problem = rejects(plaque, NAMEPLATE, List.of(text("face", "Αθηνά")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("We can print Latin letters and six Indian scripts (Devanagari, Telugu, Tamil, Kannada, Bengali, "
                + "Gujarati); “Αθη” is not one of them yet");
        problem = rejects(plaque, NAMEPLATE, List.of(lettering("नमस्ते", "telugu", null)), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("This text (Naam) is written in Devanagari letters, but Telugu lettering was chosen; "
                + "choose Devanagari or let us pick");
        assertThat(problem.properties()).containsEntry("field", "features[0].script");
        problem = rejects(plaque, NAMEPLATE, List.of(text("face", "Asha\nRao")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Text (Naam) goes on a single line");
        // no-break spaces are spaces, as the geometry service trims them
        problem = rejects(plaque, NAMEPLATE, List.of(text("face", "\u00A0\u00A0")), ProblemCodes.VALIDATION_FAILED);
        assertThat(problem.getMessage()).isEqualTo("Type the text (Naam) to add");

        problem = rejects(plaque, NAMEPLATE, List.of(lettering("Asha", null, "Comic Sans")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).isEqualTo("The lettering style “Comic Sans” is not available yet; leave the font empty for our standard "
                + "Latin lettering");
        assertThat(problem.properties()).containsEntry("field", "features[0].font");
        // a script's own lettering belongs to that script
        problem = rejects(plaque, NAMEPLATE, List.of(lettering("नमस्ते", null, "Noto Sans Tamil")), ProblemCodes.UNSUPPORTED_FEATURE);
        assertThat(problem.getMessage()).endsWith("leave the font empty for our standard Devanagari lettering");
        rejects(plaque, NAMEPLATE, List.of(lettering("Asha", null, "Noto Sans Devanagari")), ProblemCodes.UNSUPPORTED_FEATURE);
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
        Map<String, Object> curved = new LinkedHashMap<>(text("face", "Asha"));
        curved.put("projection", "cylindrical");
        Map<String, Object> small = new LinkedHashMap<>(motif("face", "paisley"));
        small.put("scale", 0.25);
        List<List<Map<String, Object>>> bad = List.of(
                List.of(motif("back")),
                List.of(text("face", "x".repeat(17))),
                List.of(relief("face"), relief("face")),
                List.of(Map.of("type", "hero_mesh", "source", Map.of("upload_id", UPLOAD), "anchor", "face")),
                List.of(relief("face"), text("face", "Asha")),
                List.of(motif("face"), motif("face")),
                List.of(curved),
                List.of(motif("face", "peacock")),
                List.of(small),
                List.of(text("face", "Asha आशा")),
                List.of(lettering("Asha", null, "Comic Sans")));
        for (List<Map<String, Object>> features : bad) {
            assertThatThrownBy(() -> validator.validate(keychain, KEYCHAIN, features)).isInstanceOfSatisfying(ApiProblemException.class,
                    e -> assertThat(e.getMessage()).doesNotContain("relief_image", "hero_mesh", "emboss_text", "motif_id", "_mm", "anchor", "planar",
                            "cylindrical", "min_scale"));
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
        return motif(anchor, "lotus");
    }

    private static Map<String, Object> motif(String anchor, String motifId) {
        return Map.of("type", "motif", "motif_id", motifId, "anchor", anchor);
    }

    /** A text on the plaque's face with an optional {@code script} and {@code font}. */
    private static Map<String, Object> lettering(String text, String script, String font) {
        Map<String, Object> feature = new LinkedHashMap<>(text("face", text));
        if (script != null) {
            feature.put("script", script);
        }
        if (font != null) {
            feature.put("font", font);
        }
        return feature;
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

    /** A motif library held in memory; {@code available: false} is a local run without the files. */
    record StubMotifs(boolean available, List<MotifDto> list) implements Motifs {

        @Override
        public Optional<MotifDto> find(String id) {
            return list.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public Optional<byte[]> svg(String id) {
            return Optional.empty();
        }
    }
}
