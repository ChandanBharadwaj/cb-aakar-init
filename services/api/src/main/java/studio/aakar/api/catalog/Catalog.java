package studio.aakar.api.catalog;

import java.util.List;
import java.util.Optional;

/** Public API of the catalog module, used by the design module to start Shop designs and price versions. */
public interface Catalog {

    List<CatalogItemDto> items(String category, String query);

    Optional<CatalogItemDto> item(String slug);

    List<MaterialDto> materials();

    Optional<MaterialDto> material(String id);
}
