package studio.aakar.api.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the catalog module: read by the design and cart modules to start Shop designs and price
 * versions; written by the management API (ADR-0012), which owns items and materials.
 */
public interface Catalog {

    /** Every Shop item, available first (the Shop shows unavailable ones with a "Coming soon" ribbon). */
    List<CatalogItemDto> items(String category, String query);

    Optional<CatalogItemDto> item(String slug);

    /** 409 {@code slug_exists} when the slug is taken; 422 {@code unknown_material} for an unknown default material. */
    CatalogItemDto createItem(CatalogItemInput input);

    /** 404 for an unknown slug. */
    CatalogItemDto updateItem(String slug, CatalogItemInput input);

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
}
