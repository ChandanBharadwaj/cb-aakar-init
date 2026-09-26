package studio.aakar.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final PriceCalculator calculator = new PriceCalculator(POLICY);

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
        PriceBreakdown price = new PriceCalculator(exact).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

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
        PriceBreakdown price = new PriceCalculator(withPackaging).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

        assertThat(line(price, "packaging").amountPaise()).isEqualTo(4_900);
        assertThat(price.subtotalPaise()).isEqualTo(129_900); // 389 + 733 + 120 + 49 = 1291 → 1299
    }

    @Test
    void marginIsAppliedBeforeRounding() {
        PricingPolicy tenPercent = new PricingPolicy("t", 20000, Map.of("silk", 12000L), 0, 10, 9, 7900, 99900, "Shipping · Delhivery, 4 days");
        PriceBreakdown price = new PriceCalculator(tenPercent).price(BOARD_EXAMPLE, TERRACOTTA_SILK);

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

    private static PriceBreakdown.Line line(PriceBreakdown price, String code) {
        Optional<PriceBreakdown.Line> line = price.lines().stream().filter(l -> l.code().equals(code)).findFirst();
        assertThat(line).as("line " + code).isPresent();
        return line.get();
    }
}
