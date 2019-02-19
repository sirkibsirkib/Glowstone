package net.glowstone.conits;

import java.util.HashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.glowstone.entity.GlowEntity;
import net.glowstone.scheduler.YSCollector;

public class BoundMatrix {
    private static HashMap<IdPair, Float> bounds = new HashMap<>();

    private static ConitConfig conitConfig;

    private static int ticksSinceBoundReset = 0;

    private static boolean flipper = false;

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
        ticksSinceBoundReset++;
        if (ticksSinceBoundReset >= conitConfig.getTicksPerBoundRecompute()) {
            resetAllBounds();
            YSCollector.pushSummaryValue("boundreset", "Count of bound reset events", 1.0);
            YSCollector.pushSummaryValue("boundmappings", "size of the boundmap before reset", (double) bounds.size());
            //System.out.printf("RESETTING BOUNDS\n");
            ticksSinceBoundReset = 0;

            flipper = !flipper; ////// DEBUG!!!!
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
        Float f = conitConfig.getBoundFunction().apply(a, b);
        if (f != null && flipper) {
            return (Float) (f.floatValue() / (float) 9.0);
        }
        return f;
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
