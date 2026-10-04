package studio.aakar.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import studio.aakar.api.support.Contracts;

/** PLAN §7.10 with the placeholder policy from {@code packages/design-tokens/materials.json}. */
class PriceCalculatorTest {

    static final PricingPolicy POLICY = new PricingPolicy("2026-09-phase0", 20000, Map.of("matte", 8000L, "silk", 12000L), 0, 0, 9,
            7900, 99900, "Shipping · Delhivery, 4 days");
    static final PriceInputs.Material TERRACOTTA_SILK = new PriceInputs.Material("terracotta_silk", 1.24, "silk", 463);
    static final PriceInputs.Material TERRACOTTA_MATTE = new PriceInputs.Material("terracotta_matte", 1.24, "matte", 420);
    static final PriceInputs.Material BASIC_WHITE = new PriceInputs.Material("basic_white", 1.24, "matte", 380);

    /** 84 g at 1.24 g/cm³ needs 67.74 cm³ of extruded filament (the 41.6 cm³ in the brief is the part's solid volume, not the extruded one). */
    static final PriceInputs.PrintEstimate BOARD_EXAMPLE = new PriceInputs.PrintEstimate(13_200, 67.74);

    private final PriceCalculator calculator = new PriceCalculator(fixed(POLICY));

    @Test
    void boardExample_terracottaSilk_84g_3h40m_isRupees1249() {
        PriceBreakdown price = calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK);

        assertThat(price.currency()).isEqualTo("INR");
        assertThat(price.materialId()).isEqualTo("terracotta_silk");
        assertThat(price.massG()).isEqualTo(84.0);
        assertThat(price.printSeconds()).isEqualTo(13_200);
        assertThat(line(price, "material").label()).isEqualTo("Material · 84 g");
        assertThat(line(price, "material").amountPaise()).isEqualTo(38_900);        // ₹389
        assertThat(line(price, "machine_time").label()).isEqualTo("Print time · 3 h 40 m");
        assertThat(line(price, "machine_time").amountPaise()).isEqualTo(73_300);    // ₹733 (3.667 h × ₹200)
        assertThat(line(price, "finishing").label()).isEqualTo("Hand sanding & sealing");
        assertThat(line(price, "finishing").amountPaise()).isEqualTo(12_000);       // ₹120
        assertThat(price.lines()).extracting(PriceBreakdown.Line::code).doesNotContain("packaging");
        assertThat(price.subtotalPaise()).isEqualTo(124_900);                       // 1242 → 1249
        assertThat(price.shippingPaise()).isZero();
        assertThat(price.shippingLabel()).isEqualTo("Shipping · Delhivery, 4 days · Free");
        assertThat(price.totalPaise()).isEqualTo(124_900);                          // ₹1,249
        assertThat(price.policyVersion()).isEqualTo("2026-09-phase0");
    }

    @Test
    void contractsExample_51_6cm3_is64gAndRupees1149() {
        // packages/contracts/examples/design.completed.example.json → the Shop card's "64 g"
        PriceBreakdown price = calculator.price(new PriceInputs.PrintEstimate(13_200, 51.6), TERRACOTTA_SILK);

        assertThat(line(price, "material").label()).isEqualTo("Material · 64 g");
        assertThat(line(price, "material").amountPaise()).isEqualTo(29_600);
        assertThat(price.subtotalPaise()).isEqualTo(114_900);
        assertThat(price.totalPaise()).isEqualTo(114_900);
        assertThat(price.totalPaise() % 1000).isEqualTo(900);
    }

    @Test
    void subtotalRoundsUpToTheNearestRupeeEndingIn9() {
        assertThat(PriceCalculator.roundUpToRupeeEndingIn(124_200, 9)).isEqualTo(124_900);
        assertThat(PriceCalculator.roundUpToRupeeEndingIn(124_900, 9)).isEqualTo(124_900);
        assertThat(PriceCalculator.roundUpToRupeeEndingIn(125_000, 9)).isEqualTo(125_900);
        assertThat(PriceCalculator.roundUpToRupeeEndingIn(124_850, 9)).isEqualTo(124_900); // ceil to a rupee first
        assertThat(PriceCalculator.roundUpToRupeeEndingIn(100, 9)).isEqualTo(900);
    }

    @Test
    void linesRoundToWholeRupees() {
        assertThat(PriceCalculator.wholeRupees(38_891.6)).isEqualTo(38_900);
        assertThat(PriceCalculator.wholeRupees(73_333.3)).isEqualTo(73_300);
        assertThat(PriceCalculator.wholeRupees(49)).isZero();
        assertThat(PriceCalculator.wholeRupees(50)).isEqualTo(100);
    }

    @Test
    void shippingIsChargedBelowTheFreeThreshold() {
        // 10 cm³ × 1.24 = 12.4 g × ₹3.80 = ₹47; 30 min × ₹200 = ₹100; matte ₹80 → ₹227 → ₹229
        PriceBreakdown price = calculator.price(new PriceInputs.PrintEstimate(1_800, 10), BASIC_WHITE);

        assertThat(price.subtotalPaise()).isEqualTo(22_900);
        assertThat(price.shippingPaise()).isEqualTo(7_900);
        assertThat(price.shippingLabel()).isEqualTo("Shipping · Delhivery, 4 days");
        assertThat(price.totalPaise()).isEqualTo(30_800);
        assertThat(line(price, "machine_time").label()).isEqualTo("Print time · 30 m");
    }

    @Test
    void shippingIsFreeAtExactlyTheThreshold() {
        PricingPolicy exact = new PricingPolicy("t", 20000, Map.of("silk", 12000L), 0, 0, 9, 7900, 124_900, "Shipping · Delhivery, 4 days");
        PriceBreakdown price = new PriceCalculator(fixed(exact)).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

        assertThat(price.subtotalPaise()).isEqualTo(124_900);
        assertThat(price.shippingPaise()).isZero();
    }

    @Test
    void finishingFeeDependsOnFinishClass() {
        PriceBreakdown silk = calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK);
        PriceBreakdown matte = calculator.price(BOARD_EXAMPLE, TERRACOTTA_MATTE);

        assertThat(line(silk, "finishing").label()).isEqualTo("Hand sanding & sealing");
        assertThat(line(silk, "finishing").amountPaise()).isEqualTo(12_000);
        assertThat(line(matte, "finishing").label()).isEqualTo("Hand finishing");
        assertThat(line(matte, "finishing").amountPaise()).isEqualTo(8_000);
        // same mass, different rate: 84 g × ₹4.20 = ₹353 (352.8 rounded)
        assertThat(line(matte, "material").amountPaise()).isEqualTo(35_300);
        assertThat(matte.subtotalPaise()).isEqualTo(116_900); // 353 + 733 + 80 = 1166 → 1169
    }

    @Test
    void packagingLineOnlyWhenTheFeeIsPositive() {
        PricingPolicy withPackaging = new PricingPolicy("t", 20000, Map.of("silk", 12000L), 4900, 0, 9, 7900, 99900, "Shipping · Delhivery, 4 days");
        PriceBreakdown price = new PriceCalculator(fixed(withPackaging)).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

        assertThat(line(price, "packaging").amountPaise()).isEqualTo(4_900);
        assertThat(price.subtotalPaise()).isEqualTo(129_900); // 389 + 733 + 120 + 49 = 1291 → 1299
    }

    @Test
    void marginIsAppliedBeforeRounding() {
        PricingPolicy tenPercent = new PricingPolicy("t", 20000, Map.of("silk", 12000L), 0, 10, 9, 7900, 99900, "Shipping · Delhivery, 4 days");
        PriceBreakdown price = new PriceCalculator(fixed(tenPercent)).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

        assertThat(price.subtotalPaise()).isEqualTo(136_900); // 1242 × 1.1 = 1366.2 → 1369
    }

    @Test
    void formatsPrintTime() {
        assertThat(PriceCalculator.formatDuration(13_200)).isEqualTo("3 h 40 m");
        assertThat(PriceCalculator.formatDuration(1_800)).isEqualTo("30 m");
        assertThat(PriceCalculator.formatDuration(3_599)).isEqualTo("1 h 0 m");
        assertThat(PriceCalculator.formatDuration(7_200)).isEqualTo("2 h 0 m");
    }

    @Test
    void outputValidatesAgainstPriceBreakdownSchema() {
        Path schema = Contracts.contracts("schemas/price-breakdown.v1.json");
        Assumptions.assumeTrue(Files.exists(schema), () -> "Skipping: " + schema + " not found");
        ObjectMapper json = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        JsonNode document = json.valueToTree(calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK));

        assertThat(Contracts.validate(schema, document)).isEmpty();
        assertThat(document.get("subtotal_paise").asLong()).isEqualTo(124_900);
        assertThat(document.get("lines").get(0).get("code").asText()).isEqualTo("material");
    }

    @Test
    void shippingForACartSubtotalFollowsThePolicy() {
        assertThat(PriceCalculator.shipping(99_900, POLICY)).isEqualTo(new PriceCalculator.Shipping(0, "Shipping · Delhivery, 4 days · Free"));
        assertThat(PriceCalculator.shipping(99_800, POLICY)).isEqualTo(new PriceCalculator.Shipping(7_900, "Shipping · Delhivery, 4 days"));
    }

    @Test
    void explicitPolicyOverridesTheStoresActiveOne() {
        PricingPolicy other = POLICY.withVersion("2027-01-test");
        assertThat(calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK, other).policyVersion()).isEqualTo("2027-01-test");
        assertThat(calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK).policyVersion()).isEqualTo("2026-09-phase0");
    }

    // ---- outcome families: hardware, setup and the family minimum (plan §5) -------------------------------------------

    /** The seed policy of 2026-10-carriers: 30 % hardware markup, keychain minimum ₹249, raw print minimum ₹349 + ₹99 setup. */
    static final PricingPolicy CARRIERS = new PricingPolicy("2026-10-carriers", 20000, Map.of("matte", 8000L, "silk", 12000L), 0, 0, 9, 7900, 99900,
            "Shipping · Delhivery, 4 days", 30, Map.of(
                    "keychain", new PricingPolicy.FamilyRule(24_900, 0, List.of()),
                    "raw_print", new PricingPolicy.FamilyRule(34_900, 9_900, List.of())));
    static final PriceInputs.Hardware SPLIT_RING = new PriceInputs.Hardware("split_ring_25", "Steel split ring 25 mm", 1, 300);
    static final PriceInputs.PrintEstimate CONTRACTS_EXAMPLE = new PriceInputs.PrintEstimate(13_200, 51.6);
    static final PriceInputs.PrintEstimate TINY = new PriceInputs.PrintEstimate(1_800, 10);

    private final PriceCalculator carriers = new PriceCalculator(fixed(CARRIERS));

    @Test
    void boardExampleIsUnchangedUnderTheCarriersPolicyWithoutAContext() {
        PriceBreakdown price = carriers.price(BOARD_EXAMPLE, TERRACOTTA_SILK);
        assertThat(price.subtotalPaise()).isEqualTo(124_900); // ₹1,249: the markup and the rules need a family and hardware
        assertThat(price.lines()).extracting(PriceBreakdown.Line::code).containsExactly("material", "machine_time", "finishing");
        assertThat(price.familyId()).isNull();
        assertThat(price.minimumSubtotalPaise()).isNull();
        // a family without rules and without hardware prices exactly the same, only named
        PriceBreakdown stand = carriers.price(BOARD_EXAMPLE, TERRACOTTA_SILK, new PriceInputs.Context("phone_stand", List.of()));
        assertThat(stand.subtotalPaise()).isEqualTo(124_900);
        assertThat(stand.lines()).isEqualTo(price.lines());
        assertThat(stand.familyId()).isEqualTo("phone_stand");
    }

    @Test
    void hardwareIsOneLineAtCostPlusTheMarkup() {
        PriceBreakdown price = carriers.price(CONTRACTS_EXAMPLE, TERRACOTTA_SILK, new PriceInputs.Context("keychain", List.of(SPLIT_RING)));

        PriceBreakdown.Line hardware = line(price, "hardware");
        assertThat(hardware.label()).isEqualTo("Steel split ring 25 mm · 1");
        assertThat(hardware.amountPaise()).isEqualTo(400);  // ₹3 × 1.3 = ₹3.90 → ₹4
        assertThat(hardware.detail()).isEqualTo("split_ring_25 × 1 at ₹3 + 30% sourcing");
        assertThat(price.lines()).extracting(PriceBreakdown.Line::code).containsExactly("material", "machine_time", "finishing", "hardware");
        assertThat(price.subtotalPaise()).isEqualTo(115_900);  // 296 + 733 + 120 + 4 = 1153 → 1159, above the ₹249 minimum
        assertThat(price.minimumSubtotalPaise()).isNull();
        assertThat(price.familyId()).isEqualTo("keychain");

        PriceBreakdown two = carriers.price(CONTRACTS_EXAMPLE, TERRACOTTA_SILK, new PriceInputs.Context("fridge_magnet", List.of(
                new PriceInputs.Hardware("magnet_d10x3", "Neodymium disc magnet 10 × 3 mm", 2, 1500), SPLIT_RING)));
        assertThat(line(two, "hardware").label()).isEqualTo("Neodymium disc magnet 10 × 3 mm · 2 + Steel split ring 25 mm · 1");
        assertThat(line(two, "hardware").amountPaise()).isEqualTo(4_300); // (2 × ₹15 + ₹3) × 1.3 = ₹42.90 → ₹43
        assertThat(two.lines().stream().filter(l -> l.code().equals("hardware"))).hasSize(1);

        // without a markup the parts cost what they cost
        PricingPolicy noMarkup = new PricingPolicy("t", 20000, Map.of("silk", 12000L), 0, 0, 9, 7900, 99900, "Shipping", 0, Map.of());
        assertThat(line(carriers.price(CONTRACTS_EXAMPLE, TERRACOTTA_SILK, noMarkup, new PriceInputs.Context("keychain", List.of(SPLIT_RING))),
                "hardware").amountPaise()).isEqualTo(300);
    }

    @Test
    void theSetupFeeIsItsOwnLine() {
        PriceBreakdown price = carriers.price(new PriceInputs.PrintEstimate(5_400, 20), BASIC_WHITE, new PriceInputs.Context("raw_print", List.of()));

        assertThat(price.lines()).extracting(PriceBreakdown.Line::code).containsExactly("material", "machine_time", "finishing", "setup");
        assertThat(line(price, "setup").label()).isEqualTo("Studio setup");
        assertThat(line(price, "setup").amountPaise()).isEqualTo(9_900);
        // 20 cm³ × 1.24 = 24.8 g × ₹3.80 = ₹94; 1.5 h × ₹200 = ₹300; matte ₹80; setup ₹99 → 573 → ₹579, above the ₹349 minimum
        assertThat(price.subtotalPaise()).isEqualTo(57_900);
        assertThat(price.minimumSubtotalPaise()).isNull();
        assertThat(price.lines()).extracting(PriceBreakdown.Line::code).doesNotContain("hardware");
    }

    @Test
    void theFamilyMinimumLiftsASmallPiece() {
        // ₹47 + ₹100 + ₹80 + ₹4 = ₹231 → ₹239, lifted to the keychain minimum of ₹249
        PriceBreakdown keychain = carriers.price(TINY, BASIC_WHITE, new PriceInputs.Context("keychain", List.of(SPLIT_RING)));
        assertThat(keychain.subtotalPaise()).isEqualTo(24_900);
        assertThat(keychain.minimumSubtotalPaise()).isEqualTo(24_900);
        assertThat(keychain.shippingPaise()).isEqualTo(7_900);
        assertThat(keychain.totalPaise()).isEqualTo(32_800);
        assertThat(keychain.lines().stream().mapToLong(PriceBreakdown.Line::amountPaise).sum()).isEqualTo(23_100); // the lines stay honest

        // the raw minimum applies on top of the setup line: 47 + 100 + 80 + 99 = 326 → ₹329 → ₹349
        PriceBreakdown raw = carriers.price(TINY, BASIC_WHITE, new PriceInputs.Context("raw_print", List.of()));
        assertThat(raw.subtotalPaise()).isEqualTo(34_900);
        assertThat(raw.minimumSubtotalPaise()).isEqualTo(34_900);

        // a minimum that is not a price ending in 9 is rounded like any subtotal
        PricingPolicy odd = new PricingPolicy("t", 20000, Map.of("matte", 8000L), 0, 0, 9, 7900, 99900, "Shipping", 0,
                Map.of("keychain", new PricingPolicy.FamilyRule(25_000, 0, List.of())));
        assertThat(carriers.price(TINY, BASIC_WHITE, odd, new PriceInputs.Context("keychain", List.of())).subtotalPaise()).isEqualTo(25_900);
        assertThat(PriceCalculator.minimumSubtotal(odd, "keychain")).contains(25_900L);
        assertThat(PriceCalculator.minimumSubtotal(odd, "fridge_magnet")).isEmpty();
        assertThat(PriceCalculator.minimumSubtotal(odd, null)).isEmpty();
        assertThat(carriers.minimumSubtotal("keychain")).contains(24_900L);
    }

    @Test
    void aBreakdownWithHardwareSetupAndMinimumValidatesAgainstTheSchema() {
        Path schema = Contracts.contracts("schemas/price-breakdown.v1.json");
        Assumptions.assumeTrue(Files.exists(schema), () -> "Skipping: " + schema + " not found");
        ObjectMapper json = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        PricingPolicy both = new PricingPolicy("t", 20000, Map.of("matte", 8000L), 0, 0, 9, 7900, 99900, "Shipping", 30,
                Map.of("keychain", new PricingPolicy.FamilyRule(29_900, 1_900, List.of())));
        JsonNode document = json.valueToTree(carriers.price(TINY, BASIC_WHITE, both, new PriceInputs.Context("keychain", List.of(SPLIT_RING))));

        assertThat(Contracts.validate(schema, document)).isEmpty();
        assertThat(document.get("family_id").asText()).isEqualTo("keychain");
        assertThat(document.get("minimum_subtotal_paise").asLong()).isEqualTo(29_900); // 231 + 19 = 250 → ₹259, lifted to ₹299
        assertThat(document.get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "hardware", "setup");
        // a print-only breakdown has neither field
        JsonNode plain = json.valueToTree(calculator.price(BOARD_EXAMPLE, TERRACOTTA_SILK));
        assertThat(plain.has("family_id")).isFalse();
        assertThat(plain.has("minimum_subtotal_paise")).isFalse();
    }

    /** A store pinned to one policy, for pure calculator tests. */
    static PricingPolicyStore fixed(PricingPolicy policy) {
        return new PricingPolicyStore() {
            @Override
            public PricingPolicy active() {
                return policy;
            }

            @Override
            public Optional<PricingPolicy> byVersion(String version) {
                return policy.version().equals(version) ? Optional.of(policy) : Optional.empty();
            }

            @Override
            public List<PricingPolicyInfo> history() {
                return List.of();
            }

            @Override
            public PricingPolicy publish(PricingPolicy p, String createdBy) {
                throw new UnsupportedOperationException("fixed policy");
            }
        };
    }

    private static PriceBreakdown.Line line(PriceBreakdown price, String code) {
        Optional<PriceBreakdown.Line> line = price.lines().stream().filter(l -> l.code().equals(code)).findFirst();
        assertThat(line).as("line " + code).isPresent();
        return line.get();
    }
}
