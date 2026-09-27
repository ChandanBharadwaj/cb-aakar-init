package studio.aakar.api.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the catalog module: read by the design and cart modules to start Shop designs and price
 * versions; written by the management API (ADR-0012), which owns items, materials, shelves, outcome families
 * (Avatars) and bought-in hardware.
 */
public interface Catalog {

    /** Every Shop item, available first (the Shop shows unavailable ones with a "Coming soon" ribbon). */
    List<CatalogItemDto> items(String category, String query);

    Optional<CatalogItemDto> item(String slug);

    /**
     * 409 {@code slug_exists} when the slug is taken; 422 {@code validation_failed} for a category that is not a shelf;
     * 422 {@code unknown_family} / {@code unknown_material} for an unknown family or default material.
     */
    CatalogItemDto createItem(CatalogItemInput input);

    /** 404 for an unknown slug. */
    CatalogItemDto updateItem(String slug, CatalogItemInput input);

    /** Shop shelves (catalog categories) in display order. */
    List<ShelfDto> shelves();

    /** Materials customers can order today ({@code available}), in display order. */
    List<MaterialDto> materials();

    /** Every material, including unavailable ones (management API). */
    List<MaterialDto> allMaterials();

    /** Any material by id, available or not — existing carts and orders keep pricing after a material is paused. */
    Optional<MaterialDto> material(String id);

    /** 409 {@code material_exists} when the id is taken. */
    MaterialDto createMaterial(MaterialInput input);

    /** 404 for an unknown id. */
    MaterialDto updateMaterial(String id, MaterialInput input);

    /**
     * Outcome families (Avatars) in display order. With {@code includeUnavailable} false only families that are
     * {@code available} and {@code ready} (at least one live template) are returned: the Create picker's list.
     *
     * @param kind optional filter: {@code carrier}, {@code object} or {@code raw}
     */
    List<FamilyDto> families(String kind, boolean includeUnavailable);

    /** Any family by id, available or not, with its readiness and live templates. */
    Optional<FamilyDto> family(String id);

    /** Every family, available or not (management API). */
    List<FamilyDto> allFamilies();

    /**
     * 409 {@code family_exists} when the id is taken; 422 {@code validation_failed} for an unknown shelf or an envelope
     * with {@code min > max}; 422 {@code unknown_hardware} / {@code unknown_material} for unknown SKUs or allowed materials.
     */
    FamilyDto createFamily(FamilyInput input);

    /** 404 {@code unknown_family} for an unknown id; the id in the path wins over the body. */
    FamilyDto updateFamily(String id, FamilyInput input);

    /** Every bought-in hardware item, available or not, by SKU. */
    List<HardwareItemDto> hardware();

    Optional<HardwareItemDto> hardwareItem(String sku);

    /** 409 {@code hardware_exists} when the SKU is taken. */
    HardwareItemDto createHardware(HardwareItemInput input);

    /** 404 for an unknown SKU. */
    HardwareItemDto updateHardware(String sku, HardwareItemInput input);
}
