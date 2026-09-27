package studio.aakar.api.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExperienceRepository extends JpaRepository<ExperienceEntity, String> {

    List<ExperienceEntity> findAllByOrderBySortOrderAscIdAsc();

    Optional<ExperienceEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);
}
