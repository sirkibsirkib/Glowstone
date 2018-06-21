package net.glowstone.conits;

import java.util.HashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.glowstone.entity.GlowEntity;

public class BoundMatrix {
    private static HashMap<IdPair, Float> bounds = new HashMap<>();

    private static ConitConfig conitConfig;

    private static int ticksSinceReset = 0;

    @Getter
    private static int ticksToStale = 0;

    /**
     * Returns the max staleness bound between two given entities
     * Return value of `null` represents an infinite bound. (ie never sync)
     * and only occurs when the entities exist in different worlds.
     */
    public static Float getBoundBetween(GlowEntity a, GlowEntity b) {
        assert a != null;
        assert b != null;
        IdPair pair = new IdPair(a.getEntityId(), b.getEntityId());
        return bounds.computeIfAbsent(pair, (k) -> computeBound(a, b));
    }

    public static void initialize(ConitConfig conitConfig) {
        BoundMatrix.conitConfig = conitConfig;
    }

    /**
     * resets the bounds map if its been long enough.
     */
    public static void pulse() {
        ticksSinceReset++;
        ticksToStale--;
        if (ticksSinceReset >= conitConfig.getTicksPerRecompute()) {
            resetAllBounds();
            System.out.printf("RESETTING BOUNDS\n");
            ticksSinceReset = 0;
        }
        if (ticksToStale < 0) {
            ticksToStale = conitConfig.getTicksPerStaleness()-1;
        }
    }

    /**
     * This clears current mappings.
     * Subsequent `getBoundBetween` calls will lazily recompute the bounds.
     */
    public static void resetAllBounds() {
        bounds.clear();
    }

    private static Float computeBound(GlowEntity a, GlowEntity b) {
        Float x = conitConfig.getBoundFunction().apply(a, b);
        System.out.printf("COMPUTED BOUND BETWEEN %s %s (%f)\n", a, b, x);
        return x;
    }

    @EqualsAndHashCode
    private static class IdPair {
        private final int lesser;
        private final int greater;

        IdPair(int a, int b) {
            // Ordering must just be stable over inversions of a and b.
            if (a <= b) {
                this.lesser = a;
                this.greater = b;
            } else {
                this.lesser = b;
                this.greater = a;
            }
        }
    }
}
