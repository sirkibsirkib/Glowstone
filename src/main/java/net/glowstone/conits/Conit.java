package net.glowstone.conits;

import com.flowpowered.network.Message;
import java.util.HashMap;
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

    /**
     * Constructs a conit for this player. Manages which messages
     * the glowplayer will collect and send.
     */
    public Conit(GlowPlayer myself, ConitConfig conitConfig) {
        this.distances = new HashMap<>();
        this.myself = myself;
        this.conitConfig = conitConfig;
    }

    /**
     * Compute the 'conit weight' of the given message.
     * If the message is not relevant to the conit mechanism `null` is returned.
     * Otherwise, some float value is returned corresponding to the weight.
     * These non-null weights should be fed with `feedMessageWeight`.
     */
    public Float messageWeight(Message message, Entity from) {
        if (this.conitConfig.getEntityPredicate().test(from)) {
            System.out.println("test pass");
            return this.conitConfig.getWeigh().apply(message);
        } else {
            System.out.println("test fail");
            return null;
        }
    }

    public boolean feedStaleness(GlowEntity from) {
        Integer fromId = from.getEntityId();
        Float dist = distances.get(fromId);
        if (dist != null) {
            // null represents infinite distance (must update)
            return feedMessageWeight(
                conitConfig.getStalenessFunction().apply(dist), from);
        } else {
            return feedMessageWeight(
                conitConfig.getStalenessFunction().apply(0.0f), from);
        }
    }

    /**
     * Incriment the staleness of the conit w.r.t the given entity
     * by the provided weight. Exceeding the bounds will trigger a synch message
     * being sent from the 'from' entity to 'this.myself' and reset staleness.
     * @param messageWeight Input return of Conit::messageWeight here.
     * @return true if there was a sync event
     */
    public boolean feedMessageWeight(float messageWeight, GlowEntity from) {
        if (messageWeight == 0.0f) {
            return false;
        }
        Integer fromId = from.getEntityId();
        Float dist = distances.get(fromId);
        if (dist != null) { // null represents infinite distance (must update)
            dist += messageWeight;
            Float bound = BoundMatrix.getBoundBetween(myself, from);
            System.out.printf("[%f / %f]\n", dist, bound);
            if (bound == null || dist < bound) {
                // no need to sync yet!
                distances.put(fromId, dist);
                return false;
            }
        }
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
