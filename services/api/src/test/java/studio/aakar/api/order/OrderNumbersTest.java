package studio.aakar.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** ADR-0007: {@code AK-000001}. */
class OrderNumbersTest {

    @Test
    void zeroPadsToSixDigits() {
        assertThat(OrderNumbers.format(1)).isEqualTo("AK-000001");
        assertThat(OrderNumbers.format(42)).isEqualTo("AK-000042");
        assertThat(OrderNumbers.format(999_999)).isEqualTo("AK-999999");
        assertThat(OrderNumbers.format(1_000_000)).isEqualTo("AK-1000000"); // grows rather than wraps
    }

    @Test
    void refusesNonPositiveSequences() {
        assertThatThrownBy(() -> OrderNumbers.format(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
