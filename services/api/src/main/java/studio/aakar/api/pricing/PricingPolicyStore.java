package studio.aakar.api.pricing;

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

    /** Every published version, newest first. */
    List<PricingPolicyInfo> history();

    /**
     * Stores {@code policy} as a new version and makes it the active one. The version must be new
     * (409 {@code policy_version_exists} otherwise).
     *
     * @param createdBy who published it (a staff account or {@code seed})
     */
    PricingPolicy publish(PricingPolicy policy, String createdBy);

    /** Header of a stored policy version. */
    record PricingPolicyInfo(String version, boolean active, java.time.Instant createdAt, String createdBy) {
    }
}
