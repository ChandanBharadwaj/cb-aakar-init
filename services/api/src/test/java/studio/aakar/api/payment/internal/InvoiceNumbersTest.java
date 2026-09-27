package studio.aakar.api.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvoiceNumbersTest {

    @Test
    void yearThenSixDigitSequence() {
        assertThat(InvoiceNumbers.format(2026, 1)).isEqualTo("INV-2026-000001");
        assertThat(InvoiceNumbers.format(2026, 123_456)).isEqualTo("INV-2026-123456");
        assertThat(InvoiceNumbers.format(2027, 7)).isEqualTo("INV-2027-000007");
        assertThat(InvoiceNumbers.format(2026, 1)).matches("INV-\\d{4}-\\d{6}");
    }

    @Test
    void refusesNonPositiveSequences() {
        assertThatThrownBy(() -> InvoiceNumbers.format(2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
