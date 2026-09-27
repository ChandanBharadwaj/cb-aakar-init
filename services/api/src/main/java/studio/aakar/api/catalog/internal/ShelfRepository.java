package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShelfRepository extends JpaRepository<ShelfEntity, String> {

    List<ShelfEntity> findAllByOrderBySortOrderAscIdAsc();
}
