package studio.aakar.api.media.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContentTermRepository extends JpaRepository<ContentTermEntity, UUID> {

    List<ContentTermEntity> findAllByOrderByNormalisedTermAsc();

    List<ContentTermEntity> findByActiveTrue();

    Optional<ContentTermEntity> findByNormalisedTerm(String normalisedTerm);
}
