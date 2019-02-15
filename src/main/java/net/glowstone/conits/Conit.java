package net.glowstone.conits;

import com.flowpowered.network.Message;
import java.util.HashMap;
import java.util.Random;
import net.glowstone.entity.GlowEntity;
import net.glowstone.entity.GlowLivingEntity;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.net.GlowSession;
import net.glowstone.net.message.play.entity.EntityHeadRotationMessage;
import net.glowstone.net.message.play.entity.EntityTeleportMessage;
import net.glowstone.util.Position;
import org.bukkit.Location;
import org.bukkit.entity.Entity;


public class Conit {

    // constants
    public static final float NEW_DISTANCE_WEIGHT =  99999.0f;
    public static final float TICK_TIME_WEIGHT    =  0.03f;

    // private fields
    private final HashMap<Integer, Float> distances;
    private final GlowPlayer myself;
    private final ConitConfig conitConfig;
    private HashMap<Integer, Float> previousDistances;
    private int ticksSinceClear;
    private long lastStalenessCheckAt;

    /**
     * Constructs a conit for this player. Manages which messages
     * the glowplayer will collect and send.
     */
    public Conit(GlowPlayer myself, ConitConfig conitConfig) {
        this.distances = new HashMap<>();
        this.previousDistances = new HashMap<>();
        this.myself = myself;
        this.conitConfig = conitConfig;
        this.ticksSinceClear = new Random().nextInt(conitConfig.getTicksPerConitClear());
        this.lastStalenessCheckAt = System.currentTimeMillis();
    }

    /**
     * Compute the 'conit weight' of the given message.
     * If the message is not relevant to the conit mechanism `null` is returned.
     * Otherwise, some float value is returned corresponding to the weight.
     * These non-null weights should be fed with `feedMessageWeight`.
     */
    public Float messageWeight(Message message, Entity from) {
        if (this.conitConfig.getEntityPredicate().test(from)) {
            //System.out.println("test pass");
            return this.conitConfig.getWeigh().apply(message);
        } else {
            //System.out.println("test fail");
            return null;
        }
    }

    /**
     * Returns true if its time for a staleness pulse. when `true` is returned,
     * the counter has already been incremented for the next time.
     */
    public boolean checkAndMaybeSetStalenesClock() {
        long now = System.currentTimeMillis();
        if (now - lastStalenessCheckAt >= conitConfig.getStalenessPeriodMillis()) {
            lastStalenessCheckAt = now;
            return true;
        }
        return false;
    }

    /**
     * Call once per tick. Performs maintenance on distance values.
     */
    public void tick() {
        ticksSinceClear++;
        if (ticksSinceClear >= conitConfig.getTicksPerConitClear()) {
            ticksSinceClear = 0;
            // System.out.println("clear conit");
            for (Integer key : previousDistances.keySet()) {
                if (distances.get(key) == previousDistances.get(key)) {
                    // the value has probably remained unchanged!
                    distances.remove(key);
                    // System.out.println("remove");
                } else {
                    // System.out.println("keep");
                }
            }
            previousDistances = (HashMap) distances.clone();
        }
    }

    /**
     * Feed staleness wrt. the given entity (ie: increment distance without associated update msg)
     * simulates some anonymous message-weight being applied, where the weight is
     * determined by the configured staleness function
     * @return true if there was a sync event
     */
    public boolean feedStaleness(GlowEntity from, boolean isInLineOfSight) {
        Integer fromId = from.getEntityId();
        Float dist = distances.get(fromId);
        if (dist != null) {
            // null represents infinite distance (must update)
            return feedMessageWeight(
                conitConfig.getStalenessFunction().apply(dist), from, isInLineOfSight);
        } else {
            return feedMessageWeight(
                conitConfig.getStalenessFunction().apply(0.0f), from, isInLineOfSight);
        }
    }

    /**
     * Incriment the staleness of the conit w.r.t the given entity
     * by the provided weight. Exceeding the bounds will trigger a synch message
     * being sent from the 'from' entity to 'this.myself' and reset staleness.
     * @param messageWeight Input return of Conit::messageWeight here.
     * @return true if there was a sync event
     */
    public boolean feedMessageWeight(float messageWeight, GlowEntity from, boolean isInLineOfSight) {
        if (messageWeight == 0.0f) {
            return false;
        }
        if (!isInLineOfSight) {
            // entity is out of sight. multiply by the configured value
            messageWeight *= conitConfig.getOutOfSightWeightMultiplier();
        }
        Integer fromId = from.getEntityId();
        Float dist = distances.get(fromId);
        if (dist != null) { // null represents infinite distance -> must sync
            dist += messageWeight;
            Float bound = BoundMatrix.getBoundBetween(myself, from);
            if (bound == null || dist < bound) {
                // no need to sync yet!
                distances.put(fromId, dist); // increment existing distance
                return false;
            }
        } // reasons it would be null: new entity-pair interaction OR cache cleared to avoid cache bloat (ie. excessive sync)

        // bound exceeded. Syncing
        pullUpdateMessage(from);
        distances.put(fromId, 0.0f);
        return true;
    }

    private void pullUpdateMessage(GlowEntity from) {
        Location location = from.getLocation();

        int entityId = from.getEntityId();
        boolean onGround = from.isOnGround();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        int yaw = Position.getIntYaw(location);
        int pitch = Position.getIntPitch(location);

        GlowSession session = myself.getSession();
        session.send(new EntityTeleportMessage(entityId, x, y, z, yaw, pitch));

        if (from instanceof GlowLivingEntity) {
            GlowLivingEntity fromCast = (GlowLivingEntity) from;
            int headYaw = Position.getIntHeadYaw(fromCast.getHeadYaw());
            session.send(new EntityHeadRotationMessage(entityId, headYaw));
        }
    }
}
