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
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
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
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toInfo).toList();
    }

    @Override
    public Optional<PricingPolicyInfo> activeInfo() {
        return repository.findFirstByActiveTrueOrderByCreatedAtDesc().map(this::toInfo);
    }

    @Override
    @Transactional
    public PricingPolicy publish(PricingPolicy policy, String createdBy) {
        return publish(policy, createdBy, null).policy();
    }

    @Override
    @Transactional
    public PricingPolicyInfo publish(PricingPolicy policy, String createdBy, String note) {
        PricingPolicyInfo published = activateNew(policy, createdBy, note);
        cache.invalidate(ACTIVE_KEY);
        return published;
    }

    private PricingPolicyInfo activateNew(PricingPolicy policy, String createdBy, String note) {
        if (repository.findByVersion(policy.version()).isPresent()) {
            throw ApiProblemException.conflict(ProblemCodes.POLICY_VERSION_EXISTS, "Policy version exists",
                    "Pricing policy version '" + policy.version() + "' already exists; publish under a new version");
        }
        repository.findByActiveTrue().forEach(PricingPolicyEntity::deactivate);
        repository.flush(); // the UPDATE must reach the partial unique index before the new active row is inserted
        PricingPolicyEntity saved = repository.saveAndFlush(new PricingPolicyEntity(policy.version(), true, json.convertValue(policy, MAP),
                note == null || note.isBlank() ? null : note.trim(), Instant.now(), createdBy == null || createdBy.isBlank() ? "unknown" : createdBy));
        log.info("Pricing policy {} published by {}", policy.version(), createdBy);
        return new PricingPolicyInfo(policy.version(), true, policy, saved.note(), saved.createdAt(), saved.createdBy());
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
                return activateNew(policy, SEED_AUTHOR, null).policy(); // inside the cache loader: the returned value is what gets cached
            }
            // The seed version exists but was deactivated by hand: re-activate it rather than duplicating it.
            repository.findByActiveTrue().forEach(PricingPolicyEntity::deactivate);
            existing.get().activate();
            repository.flush();
            return toPolicy(existing.get());
        });
    }

    private PricingPolicyInfo toInfo(PricingPolicyEntity entity) {
        return new PricingPolicyInfo(entity.version(), entity.active(), toPolicy(entity), entity.note(), entity.createdAt(), entity.createdBy());
    }

    private PricingPolicy toPolicy(PricingPolicyEntity entity) {
        PricingPolicy policy = json.convertValue(entity.policy(), PricingPolicy.class);
        return policy.version().equals(entity.version()) ? policy : policy.withVersion(entity.version());
    }
}
