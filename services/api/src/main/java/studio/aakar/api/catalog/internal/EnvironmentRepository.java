package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, String> {

    List<EnvironmentEntity> findAllByOrderBySortOrderAscIdAsc();
}
