package studio.aakar.api.design.internal;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.media.AssetUrlResolver;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceCalculator;
import studio.aakar.api.pricing.PriceInputs;

/** Assembles API responses: resolves asset URLs and attaches the price for the version's own material. */
@Component
class VersionMapper {

    private final Catalog catalog;
    private final PriceCalculator calculator;
    private final AssetUrlResolver assetUrls;

    VersionMapper(Catalog catalog, PriceCalculator calculator, AssetUrlResolver assetUrls) {
        this.catalog = catalog;
        this.calculator = calculator;
        this.assetUrls = assetUrls;
    }

    DesignVersionResponse toResponse(DesignVersionEntity v) {
        PriceBreakdown price = Optional.ofNullable(DesignSpecs.material(v.spec()))
                .flatMap(catalog::material)
                .flatMap(material -> price(v, material))
                .orElse(null);
        return new DesignVersionResponse(v.id(), v.designId(), v.versionNo(), v.parentVersionId(), v.status(), v.spec(),
                v.template(), v.assets() == null ? null : assetUrls.resolve(v.assets()), v.geometry(), v.printability(),
                v.printEstimate(), price, v.karigarNote(), v.jobId(), v.createdBy(), v.createdAt());
    }

    DesignResponse toResponse(DesignEntity design, DesignVersionEntity latest, int versionsCount) {
        VersionStatus status = latest == null ? VersionStatus.generating : latest.status();
        return new DesignResponse(design.id(), design.source(), design.catalogItemSlug(), design.title(), status,
                design.createdAt(), versionsCount, latest == null ? null : toResponse(latest));
    }

    /** Price of a ready version for any material; empty while the version has no print estimate yet. */
    Optional<PriceBreakdown> price(DesignVersionEntity v, MaterialDto material) {
        return printEstimate(v).map(estimate -> calculator.price(estimate,
                new PriceInputs.Material(material.id(), material.densityGCm3(), material.finishClass(), material.ratePerGPaise())));
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
