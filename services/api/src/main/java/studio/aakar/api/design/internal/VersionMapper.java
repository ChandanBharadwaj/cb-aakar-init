package studio.aakar.api.design.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.HardwareItemDto;
import studio.aakar.api.catalog.HardwareRef;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.media.AssetUrlResolver;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceCalculator;
import studio.aakar.api.pricing.PriceInputs;

/**
 * Assembles API responses: resolves asset URLs, names the packed hardware and attaches the price for the version's
 * own material, with the family's rules and the hardware line ({@link #context}).
 */
@Component
class VersionMapper {

    private static final Logger log = LoggerFactory.getLogger(VersionMapper.class);

    private final Catalog catalog;
    private final PriceCalculator calculator;
    private final AssetUrlResolver assetUrls;

    VersionMapper(Catalog catalog, PriceCalculator calculator, AssetUrlResolver assetUrls) {
        this.catalog = catalog;
        this.calculator = calculator;
        this.assetUrls = assetUrls;
    }

    DesignVersionResponse toResponse(DesignVersionEntity v) {
        String familyId = DesignSpecs.family(v.spec());
        Map<String, HardwareItemDto> items = hardwareItems(v.hardware());
        PriceInputs.Context context = context(familyId, v.hardware(), items);
        PriceBreakdown price = Optional.ofNullable(DesignSpecs.material(v.spec()))
                .flatMap(catalog::material)
                .flatMap(material -> price(v, material, context))
                .orElse(null);
        return new DesignVersionResponse(v.id(), v.designId(), v.versionNo(), v.parentVersionId(), v.status(), v.spec(),
                v.template(), v.assets() == null ? null : assetUrls.resolve(v.assets()), v.geometry(), v.printability(),
                v.printEstimate(), price, familyId, named(v.hardware(), items), v.karigarNote(), v.jobId(), v.createdBy(), v.createdAt());
    }

    DesignResponse toResponse(DesignEntity design, DesignVersionEntity latest, int versionsCount) {
        VersionStatus status = latest == null ? VersionStatus.generating : latest.status();
        return new DesignResponse(design.id(), design.source(), design.catalogItemSlug(), design.familyId(), design.title(), status,
                design.createdAt(), versionsCount, latest == null ? null : toResponse(latest));
    }

    /** Price of a ready version for any material; empty while the version has no print estimate yet. */
    Optional<PriceBreakdown> price(DesignVersionEntity v, MaterialDto material) {
        return price(v, material, context(DesignSpecs.family(v.spec()), v.hardware(), hardwareItems(v.hardware())));
    }

    /** The version's family and its hardware with names and unit costs from the catalog (an unknown SKU is left out). */
    PriceInputs.Context context(DesignVersionResponse version) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HardwareRef ref : version.hardware()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sku", ref.sku());
            row.put("qty", ref.qty());
            rows.add(row);
        }
        String familyId = version.familyId() != null ? version.familyId() : DesignSpecs.family(version.spec());
        return context(familyId, rows, hardwareItems(rows));
    }

    private Optional<PriceBreakdown> price(DesignVersionEntity v, MaterialDto material, PriceInputs.Context context) {
        return printEstimate(v).map(estimate -> calculator.price(estimate,
                new PriceInputs.Material(material.id(), material.densityGCm3(), material.finishClass(), material.ratePerGPaise()), context));
    }

    private static PriceInputs.Context context(String familyId, List<Map<String, Object>> hardware, Map<String, HardwareItemDto> items) {
        List<PriceInputs.Hardware> parts = new ArrayList<>();
        for (Map<String, Object> row : hardware) {
            String sku = sku(row);
            int qty = qty(row);
            HardwareItemDto item = sku == null ? null : items.get(sku);
            if (item == null || qty <= 0) {
                log.warn("Hardware {} × {} is not in the catalog; left out of the price", sku, qty);
                continue;
            }
            parts.add(new PriceInputs.Hardware(sku, item.name(), qty, item.unitCostPaise()));
        }
        return new PriceInputs.Context(familyId, parts);
    }

    private Map<String, HardwareItemDto> hardwareItems(List<Map<String, Object>> hardware) {
        Map<String, HardwareItemDto> items = new LinkedHashMap<>();
        for (Map<String, Object> row : hardware) {
            String sku = sku(row);
            if (sku != null && !items.containsKey(sku)) {
                catalog.hardwareItem(sku).ifPresent(item -> items.put(sku, item));
            }
        }
        return items;
    }

    private static List<HardwareRef> named(List<Map<String, Object>> hardware, Map<String, HardwareItemDto> items) {
        List<HardwareRef> refs = new ArrayList<>();
        for (Map<String, Object> row : hardware) {
            String sku = sku(row);
            if (sku != null) {
                HardwareItemDto item = items.get(sku);
                refs.add(new HardwareRef(sku, qty(row), item == null ? null : item.name()));
            }
        }
        return refs;
    }

    private static String sku(Map<String, Object> row) {
        return row.get("sku") instanceof String s && !s.isBlank() ? s : null;
    }

    private static int qty(Map<String, Object> row) {
        return row.get("qty") instanceof Number n ? n.intValue() : 1;
    }

    private static Optional<PriceInputs.PrintEstimate> printEstimate(DesignVersionEntity v) {
        Map<String, Object> estimate = v.printEstimate();
        if (v.status() != VersionStatus.ready || estimate == null) {
            return Optional.empty();
        }
        Object seconds = estimate.get("print_seconds");
        Object volume = estimate.get("extruded_volume_cm3");
        if (!(seconds instanceof Number s) || !(volume instanceof Number vol)) {
            return Optional.empty();
        }
        return Optional.of(new PriceInputs.PrintEstimate(s.intValue(), vol.doubleValue()));
    }
}
