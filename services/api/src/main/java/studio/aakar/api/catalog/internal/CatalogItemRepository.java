package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, String> {

    List<CatalogItemEntity> findAllByOrderByAvailableDescBasePricePaiseAscNameAsc();
}
