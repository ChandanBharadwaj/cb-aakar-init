package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HardwareItemRepository extends JpaRepository<HardwareItemEntity, String> {

    List<HardwareItemEntity> findAllByOrderBySkuAsc();
}
