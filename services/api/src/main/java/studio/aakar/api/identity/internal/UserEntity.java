package studio.aakar.api.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.identity.UserDto;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String phone;
    private String name;
    private String email;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    UserEntity(String phone, Instant now) {
        this.phone = phone;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    String phone() {
        return phone;
    }

    void update(String name, String email, Instant now) {
        if (name != null) {
            this.name = name.isBlank() ? null : name.trim();
        }
        if (email != null) {
            this.email = email.isBlank() ? null : email.trim();
        }
        this.updatedAt = now;
    }

    UserDto toDto() {
        return new UserDto(id, phone, name, email, createdAt);
    }
}
