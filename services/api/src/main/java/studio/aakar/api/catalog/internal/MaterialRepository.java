package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MaterialRepository extends JpaRepository<MaterialEntity, String> {

    List<MaterialEntity> findAllByOrderBySortOrderAscIdAsc();
}
