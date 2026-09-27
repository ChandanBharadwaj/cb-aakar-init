package studio.aakar.api.pricing.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyProperties;
import studio.aakar.api.pricing.PricingPolicyStore;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * {@code pricing_policies}-backed store. The active policy is cached for {@link #CACHE_TTL}; publishing
 * invalidates the cache on this node (other nodes catch up within the TTL). When the table holds no
 * active row (a fresh database before the seed migration ran, or every row deactivated by hand) the
 * {@code aakar.pricing.*} seed is published so pricing never stops.
 */
@Service
class DbPricingPolicyStore implements PricingPolicyStore {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    static final String SEED_AUTHOR = "seed";
    private static final String ACTIVE_KEY = "active";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final Logger log = LoggerFactory.getLogger(DbPricingPolicyStore.class);

    private final PricingPolicyRepository repository;
    private final PricingPolicyProperties seed;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Cache<String, PricingPolicy> cache;

    DbPricingPolicyStore(PricingPolicyRepository repository, PricingPolicyProperties seed, ObjectMapper json,
            TransactionTemplate transactions) {
        this(repository, seed, json, transactions, CACHE_TTL);
    }

    DbPricingPolicyStore(PricingPolicyRepository repository, PricingPolicyProperties seed, ObjectMapper json,
            TransactionTemplate transactions, Duration cacheTtl) {
        this.repository = repository;
        this.seed = seed;
        this.json = json;
        this.transactions = transactions;
        this.cache = Caffeine.newBuilder().expireAfterWrite(cacheTtl).maximumSize(1).build();
    }

    @Override
    public PricingPolicy active() {
        return cache.get(ACTIVE_KEY, key -> loadActive());
    }

    @Override
    public Optional<PricingPolicy> byVersion(String version) {
        return repository.findByVersion(version).map(this::toPolicy);
    }

    @Override
    public List<PricingPolicyInfo> history() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> new PricingPolicyInfo(e.version(), e.active(), e.createdAt(), e.createdBy()))
                .toList();
    }

    @Override
    @Transactional
    public PricingPolicy publish(PricingPolicy policy, String createdBy) {
        if (repository.findByVersion(policy.version()).isPresent()) {
            throw ApiProblemException.conflict(ProblemCodes.POLICY_VERSION_EXISTS, "Policy version exists",
                    "Pricing policy version '" + policy.version() + "' already exists; publish under a new version");
        }
        repository.findByActiveTrue().forEach(PricingPolicyEntity::deactivate);
        repository.saveAndFlush(new PricingPolicyEntity(policy.version(), true, json.convertValue(policy, MAP), Instant.now(),
                createdBy == null || createdBy.isBlank() ? "unknown" : createdBy));
        cache.invalidate(ACTIVE_KEY);
        log.info("Pricing policy {} published by {}", policy.version(), createdBy);
        return policy;
    }

    private PricingPolicy loadActive() {
        return repository.findFirstByActiveTrueOrderByCreatedAtDesc().map(this::toPolicy).orElseGet(this::publishSeed);
    }

    private PricingPolicy publishSeed() {
        log.warn("No active pricing policy in pricing_policies; activating the aakar.pricing seed {}", seed.version());
        PricingPolicy policy = seed.toPolicy();
        return transactions.execute(status -> {
            Optional<PricingPolicyEntity> existing = repository.findByVersion(policy.version());
            if (existing.isEmpty()) {
                return publish(policy, SEED_AUTHOR);
            }
            // The seed version exists but was deactivated by hand: re-activate it rather than duplicating it.
            repository.findByActiveTrue().forEach(PricingPolicyEntity::deactivate);
            existing.get().activate();
            repository.flush();
            return toPolicy(existing.get());
        });
    }

    private PricingPolicy toPolicy(PricingPolicyEntity entity) {
        PricingPolicy policy = json.convertValue(entity.policy(), PricingPolicy.class);
        return policy.version().equals(entity.version()) ? policy : policy.withVersion(entity.version());
    }
}
