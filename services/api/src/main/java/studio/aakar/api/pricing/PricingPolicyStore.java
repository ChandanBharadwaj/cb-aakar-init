package studio.aakar.api.pricing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Versioned pricing policies (ADR-0008). Exactly one version is active; publishing a new one never edits
 * an old one, so carts and orders can always name the {@code policy_version} they were priced with.
 */
public interface PricingPolicyStore {

    /** The active policy, cached for a short while (30 s). */
    PricingPolicy active();

    Optional<PricingPolicy> byVersion(String version);

    /** Every published version with its policy, newest first. */
    List<PricingPolicyInfo> history();

    /**
     * Stores {@code policy} as a new version and makes it the active one. The version must be new
     * (409 {@code policy_version_exists} otherwise).
     *
     * @param createdBy who published it (a staff account or {@code seed})
     */
    PricingPolicy publish(PricingPolicy policy, String createdBy);

    /** {@link #publish(PricingPolicy, String)} with the staff note kept on the version (management API). */
    default PricingPolicyInfo publish(PricingPolicy policy, String createdBy, String note) {
        PricingPolicy published = publish(policy, createdBy);
        return history().stream().filter(i -> i.version().equals(published.version())).findFirst()
                .orElseGet(() -> new PricingPolicyInfo(published.version(), true, published, note, Instant.now(), createdBy));
    }

    /** The active version's header, when the table has one. */
    default Optional<PricingPolicyInfo> activeInfo() {
        return history().stream().filter(PricingPolicyInfo::active).findFirst();
    }

    /** A stored policy version: header plus the policy itself and the note the publisher left. */
    record PricingPolicyInfo(String version, boolean active, PricingPolicy policy, String note, Instant createdAt, String createdBy) {
    }
}
