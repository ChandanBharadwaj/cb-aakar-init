package studio.aakar.api.admin.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.admin.StaffDto;
import studio.aakar.api.admin.StaffRole;

@Entity
@Table(name = "staff_accounts")
class StaffAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StaffAccountEntity() {
    }

    StaffAccountEntity(String email, String name, StaffRole role, String passwordHash, Instant now) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = now;
    }

    UUID id() {
        return id;
    }

    String email() {
        return email;
    }

    StaffRole role() {
        return role;
    }

    String passwordHash() {
        return passwordHash;
    }

    StaffPrincipal principal() {
        return new StaffPrincipal(id, email, name, role);
    }

    StaffDto toDto() {
        return new StaffDto(id, email, name, role);
    }
}
