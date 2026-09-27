package studio.aakar.api.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.identity.AddressDto;
import studio.aakar.api.identity.AddressInput;

@Entity
@Table(name = "addresses")
class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    private String label;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false)
    private String line1;
    private String line2;
    @Column(nullable = false)
    private String city;
    @Column(nullable = false)
    private String state;
    @Column(nullable = false)
    private String pincode;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AddressEntity() {
    }

    AddressEntity(UUID userId, AddressInput input, boolean isDefault, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        apply(input, isDefault, now);
    }

    UUID id() {
        return id;
    }

    boolean isDefault() {
        return isDefault;
    }

    void apply(AddressInput input, boolean isDefault, Instant now) {
        this.label = blankToNull(input.label());
        this.name = input.name().trim();
        this.phone = input.phone().trim();
        this.line1 = input.line1().trim();
        this.line2 = blankToNull(input.line2());
        this.city = input.city().trim();
        this.state = input.state().trim();
        this.pincode = input.pincode().trim();
        this.isDefault = isDefault;
        this.updatedAt = now;
    }

    void setDefault(boolean value, Instant now) {
        if (this.isDefault != value) {
            this.isDefault = value;
            this.updatedAt = now;
        }
    }

    AddressDto toDto() {
        return new AddressDto(id, label, name, phone, line1, line2, city, state, pincode, isDefault);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
