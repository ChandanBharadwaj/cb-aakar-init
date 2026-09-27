package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Share codes: 8 base32 characters, one per order, collisions retried. */
class ShareCodesTest {

    static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private final ShareCodeRepository repository = mock(ShareCodeRepository.class);
    private final ShareCodes codes = new ShareCodes(repository, new Random(42), "http://localhost:3000", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void generatedCodesAreEightBase32Characters() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            String code = ShareCodes.generate(random);
            assertThat(code).hasSize(8).matches("^[A-Z2-7]{8}$");
        }
        assertThat(ShareCodes.generate(new Random(1))).isNotEqualTo(ShareCodes.generate(new Random(2)));
    }

    @Test
    void mintsOnceAndReusesTheOrdersCode() {
        UUID orderId = UUID.randomUUID();
        UUID designId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(repository.existsById(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShareCodeEntity minted = codes.mintFor(orderId, designId, versionId);

        assertThat(minted.code()).matches("^[A-Z2-7]{8}$");
        assertThat(minted.orderId()).isEqualTo(orderId);
        assertThat(minted.designId()).isEqualTo(designId);
        assertThat(minted.versionId()).isEqualTo(versionId);
        assertThat(codes.link(minted.code())).isEqualTo("http://localhost:3000/k/" + minted.code());

        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(minted));
        assertThat(codes.mintFor(orderId, designId, versionId).code()).isEqualTo(minted.code());
        verify(repository, times(1)).save(any());
    }

    @Test
    void aCollidingCodeIsRetried() {
        UUID orderId = UUID.randomUUID();
        when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(repository.existsById(any())).thenReturn(true, true, false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        codes.mintFor(orderId, UUID.randomUUID(), UUID.randomUUID());

        verify(repository, times(3)).existsById(any());
        verify(repository, times(1)).save(any());
        verify(repository, never()).delete(any());
    }
}
