package studio.aakar.api.cart.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sign-in merge: same version + material adds up (cap 20); everything else moves over. */
class CartMergeTest {

    record Line(UUID versionId, String materialId, int qty) implements CartMerge.Line {
    }

    static final UUID V1 = UUID.randomUUID();
    static final UUID V2 = UUID.randomUUID();

    @Test
    void guestLinesMoveWhenTheUserHasNothingMatching() {
        Line g1 = new Line(V1, "terracotta_silk", 2);
        Line g2 = new Line(V2, "indigo_matte", 1);

        CartMerge.Plan<Line> plan = CartMerge.plan(List.of(), List.of(g1, g2));

        assertThat(plan.moved()).containsExactly(g1, g2);
        assertThat(plan.combined()).isEmpty();
        assertThat(plan.guestLines()).isEqualTo(2);
    }

    @Test
    void sameVersionAndMaterialCombineQuantities() {
        Line user = new Line(V1, "terracotta_silk", 3);
        Line guest = new Line(V1, "terracotta_silk", 2);

        CartMerge.Plan<Line> plan = CartMerge.plan(List.of(user), List.of(guest));

        assertThat(plan.moved()).isEmpty();
        assertThat(plan.combined()).containsExactly(new CartMerge.Combined<>(user, guest, 5));
        assertThat(plan.guestLines()).isEqualTo(1);
    }

    @Test
    void sameVersionDifferentMaterialIsASeparateLine() {
        Line user = new Line(V1, "terracotta_silk", 1);
        Line guest = new Line(V1, "polished_brass", 1);

        CartMerge.Plan<Line> plan = CartMerge.plan(List.of(user), List.of(guest));

        assertThat(plan.moved()).containsExactly(guest);
        assertThat(plan.combined()).isEmpty();
    }

    @Test
    void combinedQuantityIsCappedAtTwenty() {
        assertThat(CartMerge.combinedQty(15, 10)).isEqualTo(20);
        assertThat(CartMerge.combinedQty(20, 1)).isEqualTo(20);
        assertThat(CartMerge.combinedQty(1, 1)).isEqualTo(2);
        Line user = new Line(V1, "terracotta_silk", 18);
        Line guest = new Line(V1, "terracotta_silk", 5);
        assertThat(CartMerge.plan(List.of(user), List.of(guest)).combined().get(0).qty()).isEqualTo(20);
    }

    @Test
    void emptyGuestCartIsANoOp() {
        CartMerge.Plan<Line> plan = CartMerge.plan(List.of(new Line(V1, "terracotta_silk", 1)), List.of());
        assertThat(plan.guestLines()).isZero();
    }

    @Test
    void duplicateGuestLinesFoldIntoTheFirstMovedOne() {
        // cannot happen with the unique index, but the rule stays total
        Line a = new Line(V1, "terracotta_silk", 1);
        Line b = new Line(V1, "terracotta_silk", 2);

        CartMerge.Plan<Line> plan = CartMerge.plan(List.of(), List.of(a, b));

        assertThat(plan.moved()).containsExactly(a);
        assertThat(plan.combined()).containsExactly(new CartMerge.Combined<>(a, b, 3));
    }
}
