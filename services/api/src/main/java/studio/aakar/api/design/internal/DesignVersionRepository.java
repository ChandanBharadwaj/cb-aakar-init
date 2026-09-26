package studio.aakar.api.design.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DesignVersionRepository extends JpaRepository<DesignVersionEntity, UUID> {

    List<DesignVersionEntity> findByDesignIdOrderByVersionNoDesc(UUID designId);

    Optional<DesignVersionEntity> findFirstByDesignIdOrderByVersionNoDesc(UUID designId);

    int countByDesignId(UUID designId);

    @Query("select coalesce(max(v.versionNo), 0) from DesignVersionEntity v where v.designId = :designId")
    int maxVersionNo(@Param("designId") UUID designId);
}
