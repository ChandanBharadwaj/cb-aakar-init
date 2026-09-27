package studio.aakar.api.templates.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TemplateFlagRepository extends JpaRepository<TemplateFlagEntity, String> {

    List<TemplateFlagEntity> findByLiveFalse();
}
