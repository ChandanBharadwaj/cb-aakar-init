package studio.aakar.api.cart.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.shared.Identity;

@Entity
@Table(name = "carts")
class CartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "guest_id")
    private UUID guestId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartEntity() {
    }

    CartEntity(Identity owner, Instant now) {
        this.userId = owner.isUser() ? owner.id() : null;
        this.guestId = owner.isGuest() ? owner.id() : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    UUID userId() {
        return userId;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    /** {@code user} or {@code guest}, the contract's {@code owner}. */
    String owner() {
        return userId != null ? Identity.Kind.user.name() : Identity.Kind.guest.name();
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
