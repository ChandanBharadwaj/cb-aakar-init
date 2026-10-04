package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TemplateFamilyRepository extends JpaRepository<TemplateFamilyEntity, String> {

    List<TemplateFamilyEntity> findAllByOrderBySortOrderAscIdAsc();
}
