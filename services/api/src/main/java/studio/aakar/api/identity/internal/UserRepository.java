package studio.aakar.api.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPhone(String phone);

    @Query("select u.id from UserEntity u where u.phone like concat('%', :fragment, '%')")
    List<UUID> findIdsByPhoneContaining(@Param("fragment") String fragment);
}
