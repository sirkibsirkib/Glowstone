package net.glowstone.conits;

import com.flowpowered.network.Message;
import java.io.File;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import lombok.Getter;
import net.glowstone.GlowServer;
import net.glowstone.entity.GlowEntity;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.entity.passive.GlowVillager;
import net.glowstone.net.message.play.entity.EntityHeadRotationMessage;
import net.glowstone.net.message.play.entity.EntityRotationMessage;
import net.glowstone.net.message.play.entity.EntityTeleportMessage;
import net.glowstone.net.message.play.entity.RelativeEntityPositionMessage;
import net.glowstone.net.message.play.entity.RelativeEntityPositionRotationMessage;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Vehicle;
import org.jetbrains.annotations.NonNls;
import org.yaml.snakeyaml.error.YAMLException;

public final class ConitConfig {
    @Getter
    private final Predicate<Entity> entityPredicate; 

    @Getter
    private final Function<Message,Float> weigh;

    @Getter
    private final Function<Float,Float> stalenessFunction;

    @Getter
    private final int ticksPerBoundRecompute;
    @Getter
    private final int ticksPerConitClear;

    @Getter
    private final int stalenessPeriodMillis;

    @Getter
    private final double outOfSightWeightMultiplier;

    @Getter
    private final BiFunction<GlowEntity,GlowEntity,Float> boundFunction;


    /**
     * Constructs and initializes a config object for the conit system.
     */
    public ConitConfig() {
        @NonNls String configDirName = "config";
        @NonNls String configFileName = "conits.yml";

        File configDir = new File(configDirName);
        File configFile = new File(configDir, configFileName);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException e) {
            GlowServer.logger.log(Level.SEVERE, "Failed to read config: " + configFile, e);
        } catch (InvalidConfigurationException e) {
            report(configFile, e);
        }

        this.ticksPerConitClear = (int) config.getInt("ticks-per-conit-clear", 200);

        this.ticksPerBoundRecompute = (int) config.getInt("bounds.ticks-per-recompute", 60);
        this.stalenessPeriodMillis = (int) config.getInt("staleness-period-millis", 3000);
        this.outOfSightWeightMultiplier = (double) config.getDouble("out-of-sight-weight-multiplier", 0.5);

        this.weigh = defineWeighFunction(config);
        this.entityPredicate = defineEntityPredicate(config);
        this.boundFunction = defineBoundFunction(config);
        this.stalenessFunction = defineStalenessFunction(config);
    }

    private static BiFunction<GlowEntity,GlowEntity,Float> defineBoundFunction(
            YamlConfiguration config) {

        final float bounds_constant = (float) config.getDouble(
            "bounds.constant", 1.0);
        float cns = (float) config.getDouble(
            "bounds.numeric-components.constant", 0.2);
        float dist = (float) config.getDouble(
            "bounds.numeric-components.distance", 0.0);
        float distSqr = (float) config.getDouble(
            "bounds.numeric-components.distance-sqr", 1.0);

        //System.out.printf("%f - %f - %f\n", cns, dist, distSqr);

        if (dist == 0.0 && distSqr == 0.0) {
            //super lightweight
            float mult = cns * bounds_constant;
            return (a, b) -> {
                if (a.getWorld() != b.getWorld()) {
                    return null;
                } else {
                    return mult;
                }
            };
        } else if (distSqr == 0.0) {
            return (a, b) -> {
                if (a.getWorld() != b.getWorld()) {
                    return null;
                } else {
                    Location al = a.getLocation();
                    Location bl = b.getLocation();
                    double val = al.distance(bl) * dist;
                    return (
                        cns + (float) val
                        ) * bounds_constant;
                }
            };
        } else if (dist == 0.0) {
            return (a, b) -> {
                if (a.getWorld() != b.getWorld()) {
                    return null;
                } else {
                    Location al = a.getLocation();
                    Location bl = b.getLocation();
                    double val = al.distanceSquared(bl) * distSqr;
                    return (
                        cns + (float) val
                        ) * bounds_constant;
                }
            };
        } else {
            return (a, b) -> {
                if (a.getWorld() != b.getWorld()) {
                    return null;
                } else {
                    Location al = a.getLocation();
                    Location bl = b.getLocation();
                    double temp = al.distanceSquared(bl);
                    double val = (temp * distSqr) + (Math.sqrt(temp) * dist);
                    return (
                        cns + (float) val
                        ) * bounds_constant;
                }
            };
        }
    }

    private static Function<Float,Float> defineStalenessFunction(
            YamlConfiguration config) {
        final float constant = (float) config.getDouble(
            "staleness-func.constant", 5.0);
        final float multiply = (float) config.getDouble(
            "staleness-func.multiply", 1.1);
        if (multiply == 0.0) {
            return current -> current + constant;
        } else if (constant == 0.0) {
            return current -> current * multiply;
        } else {
            return current -> (current + constant) * multiply;
        }
    }

    private static Predicate<Entity> defineEntityPredicate(YamlConfiguration config) {
        final boolean players = config.getBoolean("enable.players", true);
        final boolean villagers = config.getBoolean("enable.villagers", true);
        final boolean monsters = config.getBoolean("enable.enemies", true);
        final boolean animals = config.getBoolean("enable.enemies", true);
        final boolean vehicles = config.getBoolean("enable.enemies", true);
        return entity ->
            (players && entity instanceof GlowPlayer)
            || (animals && entity instanceof Animals)
            || (monsters && entity instanceof Monster)
            || (vehicles && entity instanceof Vehicle)
            || (villagers && entity instanceof GlowVillager);
    }

    private static Function<Message,Float> defineWeighFunction(YamlConfiguration config) {
        final float movementWeightConstant = (float) config.getDouble(
            "message-type-scale.movement.constant", 0.1);
        final float movementWeightDistance = (float) config.getDouble(
            "message-type-scale.movement.distance", 3.0);
        final float headRotationWeight = (float) config.getDouble(
            "message-type-scale.head-rotation", 0.8);
        final float movementWeightRotation = (float) config.getDouble(
            "message-type-scale.rotation", 2.0);
        final float movementWeightTeleport = (float) config.getDouble(
            "message-type-scale.teleport", 10.0);

        return message -> {
            if (message instanceof EntityHeadRotationMessage) {
                return headRotationWeight;
            } else if (message instanceof RelativeEntityPositionRotationMessage) {
                if (movementWeightDistance == 0.0) {
                    return movementWeightConstant;
                }
                RelativeEntityPositionRotationMessage cast =
                    (RelativeEntityPositionRotationMessage) message;
                return (float) (Math.abs(cast.getDeltaX())
                        + Math.abs(cast.getDeltaY())
                        + Math.abs(cast.getDeltaZ()))
                    * movementWeightDistance
                    + (movementWeightConstant + movementWeightRotation);
            } else if (message instanceof RelativeEntityPositionMessage) {
                if (movementWeightDistance == 0.0) {
                    return movementWeightConstant;
                }
                RelativeEntityPositionMessage cast =
                    (RelativeEntityPositionMessage) message;
                return (float) (Math.abs(cast.getDeltaX())
                        + Math.abs(cast.getDeltaY())
                        + Math.abs(cast.getDeltaZ()))
                    * movementWeightDistance
                    + (movementWeightConstant);
            } else if (message instanceof EntityTeleportMessage) {
                return movementWeightTeleport;
            } else if (message instanceof EntityRotationMessage) {
                return movementWeightRotation;
            }
            return null;
        };
    }

    private void report(File file, InvalidConfigurationException e) {
        if (e.getCause() instanceof YAMLException) {
            GlowServer.logger.severe("Config file " + file + " isn't valid! " + e.getCause());
        } else if (e.getCause() == null || e.getCause() instanceof ClassCastException) {
            GlowServer.logger.severe("Config file " + file + " isn't valid!");
        } else {
            GlowServer.logger
                    .log(Level.SEVERE, "Cannot load " + file + ": " + e.getCause().getClass(), e);
        }
    }
}
