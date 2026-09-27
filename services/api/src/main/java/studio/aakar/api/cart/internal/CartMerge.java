package studio.aakar.api.cart.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sign-in merge rule, as a pure plan over two item lists: a guest line whose version and material
 * already sit in the user's cart is <em>combined</em> (quantities add up, capped at {@link #MAX_QTY});
 * any other guest line is <em>moved</em> as is.
 */
final class CartMerge {

    static final int MAX_QTY = 20;

    private CartMerge() {
    }

    /** What a cart line contributes to the merge. */
    interface Line {
        UUID versionId();

        String materialId();

        int qty();
    }

    record Key(UUID versionId, String materialId) {
        static Key of(Line line) {
            return new Key(line.versionId(), line.materialId());
        }
    }

    /** A guest line landing on an existing user line, with the resulting quantity. */
    record Combined<L extends Line>(L target, L source, int qty) {
    }

    record Plan<L extends Line>(List<L> moved, List<Combined<L>> combined) {
        /** Number of guest lines the plan consumes. */
        int guestLines() {
            return moved.size() + combined.size();
        }
    }

    static <L extends Line> Plan<L> plan(List<L> userLines, List<L> guestLines) {
        Map<Key, L> byKey = new HashMap<>();
        userLines.forEach(l -> byKey.putIfAbsent(Key.of(l), l));
        List<L> moved = new ArrayList<>();
        List<Combined<L>> combined = new ArrayList<>();
        for (L guest : guestLines) {
            L target = byKey.get(Key.of(guest));
            if (target == null) {
                moved.add(guest);
                byKey.put(Key.of(guest), guest);
            } else {
                combined.add(new Combined<>(target, guest, combinedQty(target.qty(), guest.qty())));
            }
        }
        return new Plan<>(List.copyOf(moved), List.copyOf(combined));
    }

    static int combinedQty(int a, int b) {
        return Math.min(MAX_QTY, Math.max(1, a + b));
    }
}
