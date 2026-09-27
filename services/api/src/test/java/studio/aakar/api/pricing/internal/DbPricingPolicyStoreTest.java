package studio.aakar.api.pricing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyProperties;
import studio.aakar.api.shared.ApiProblemException;

/** ADR-0008: the active policy comes from the table, is cached briefly, and publishing activates a new version. */
class DbPricingPolicyStoreTest {

    static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    static final PricingPolicyProperties SEED = new PricingPolicyProperties("2026-09-phase0", 20000, Map.of("matte", 8000L, "silk", 12000L),
            0, 0, 9, 7900, 99900, "Shipping · Delhivery, 4 days");

    private final PricingPolicyRepository repository = mock(PricingPolicyRepository.class);
    private DbPricingPolicyStore store;

    @BeforeEach
    void setUp() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        store = new DbPricingPolicyStore(repository, SEED, JSON, noOpTransactions(), Duration.ofMinutes(5));
    }

    @Test
    void activeComesFromTheTableAndIsCached() {
        PricingPolicy stored = SEED.toPolicy().withVersion("2026-10-v2");
        when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(row(stored, true)));

        assertThat(store.active()).isEqualTo(stored);
        assertThat(store.active().version()).isEqualTo("2026-10-v2");
        verify(repository, times(1)).findFirstByActiveTrueOrderByCreatedAtDesc();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void theRowVersionWinsOverTheJsonVersion() {
        PricingPolicyEntity row = row(SEED.toPolicy(), true);
        when(repository.findByVersion("renamed")).thenReturn(Optional.of(new PricingPolicyEntity("renamed", false, row.policy(), Instant.now(), "x")));

        assertThat(store.byVersion("renamed")).map(PricingPolicy::version).contains("renamed");
        assertThat(store.byVersion("missing")).isEmpty();
    }

    @Test
    void publishDeactivatesTheCurrentAndActivatesTheNewVersion() {
        PricingPolicyEntity current = row(SEED.toPolicy(), true);
        when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(current));
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findByVersion("2026-10-v2")).thenReturn(Optional.empty());
        assertThat(store.active().version()).isEqualTo("2026-09-phase0");

        PricingPolicy next = new PricingPolicy("2026-10-v2", 25000, Map.of("matte", 9000L, "silk", 13000L), 4900, 5, 9, 9900, 149900, "Shipping · Delhivery");
        PricingPolicy published = store.publish(next, "owner@aakar.studio");

        assertThat(published).isEqualTo(next);
        assertThat(current.active()).isFalse();
        ArgumentCaptor<PricingPolicyEntity> saved = ArgumentCaptor.forClass(PricingPolicyEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().version()).isEqualTo("2026-10-v2");
        assertThat(saved.getValue().active()).isTrue();
        assertThat(saved.getValue().createdBy()).isEqualTo("owner@aakar.studio");
        assertThat(saved.getValue().policy()).containsEntry("machine_rate_paise_per_hour", 25000L).containsEntry("shipping_label", "Shipping · Delhivery");
        assertThat(JSON.convertValue(saved.getValue().policy(), PricingPolicy.class)).isEqualTo(next);

        // The cache was invalidated: the next read hits the table again and sees the new row.
        when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(saved.getValue()));
        assertThat(store.active()).isEqualTo(next);
        verify(repository, times(2)).findFirstByActiveTrueOrderByCreatedAtDesc();
    }

    @Test
    void publishRefusesAnExistingVersion() {
        when(repository.findByVersion("2026-09-phase0")).thenReturn(Optional.of(row(SEED.toPolicy(), true)));

        assertThatThrownBy(() -> store.publish(SEED.toPolicy(), "owner"))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    assertThat(e.code()).isEqualTo("policy_version_exists");
                    assertThat(e.status().value()).isEqualTo(409);
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void anEmptyTableIsSeededFromTheProperties() {
        when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByVersion(anyString())).thenReturn(Optional.empty());
        when(repository.findByActiveTrue()).thenReturn(List.of());

        PricingPolicy active = store.active();

        assertThat(active).isEqualTo(SEED.toPolicy());
        ArgumentCaptor<PricingPolicyEntity> saved = ArgumentCaptor.forClass(PricingPolicyEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().createdBy()).isEqualTo(DbPricingPolicyStore.SEED_AUTHOR);
        assertThat(saved.getValue().active()).isTrue();
    }

    @Test
    void aDeactivatedSeedIsReactivatedRatherThanDuplicated() {
        PricingPolicyEntity dormant = row(SEED.toPolicy(), false);
        when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByVersion("2026-09-phase0")).thenReturn(Optional.of(dormant));
        when(repository.findByActiveTrue()).thenReturn(List.of());

        assertThat(store.active()).isEqualTo(SEED.toPolicy());
        assertThat(dormant.active()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    private static PricingPolicyEntity row(PricingPolicy policy, boolean active) {
        return new PricingPolicyEntity(policy.version(), active, JSON.convertValue(policy, new TypeReference<Map<String, Object>>() { }),
                Instant.parse("2026-09-27T00:00:00Z"), "seed");
    }

    private static TransactionTemplate noOpTransactions() {
        return new TransactionTemplate(new org.springframework.transaction.PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
