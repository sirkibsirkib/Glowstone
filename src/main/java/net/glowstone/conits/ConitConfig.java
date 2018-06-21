package net.glowstone.conits;

// import static com.google.common.base.Preconditions.checkArgument;
// import static com.google.common.base.Preconditions.checkNotNull;

import com.flowpowered.network.Message;
import java.io.File;
// import java.io.FileInputStream;
// import java.io.FileOutputStream;
import java.io.IOException;
// import java.io.InputStream;
// import java.io.OutputStream;
// import java.lang.Math;
// import java.net.URL;
// import java.nio.file.Paths;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Properties;
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
// import net.glowstone.util.CompatibilityBundle;
// import net.glowstone.util.DynamicallyTypedMap;
// import org.bukkit.Difficulty;
// import org.bukkit.GameMode;
import org.bukkit.Location;
// import org.bukkit.WorldType;
// import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Vehicle;
// import org.bukkit.util.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.yaml.snakeyaml.error.YAMLException;

public final class ConitConfig {
    @Getter
    private final Predicate<Entity> entityPredicate; 

    @Getter
    private final Function<Message,Float> weigh;

    @Getter
    private final float stalenessIncrement;

    @Getter
    private final int ticksPerRecompute;

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

        this.stalenessIncrement = (float) config.getDouble("error-scale.staleness", 0.0);
        this.ticksPerRecompute = (int) config.getInt("bounds.ticks-per-recompute", 20);

        this.weigh = defineWeighFunction(config);
        this.entityPredicate = defineEntityPredicate(config);
        this.boundFunction = defineBoundFunction(config);
    }

    private static BiFunction<GlowEntity,GlowEntity,Float> defineBoundFunction(
            YamlConfiguration config) {
        final float bounds_constant = (float) config.getDouble(
            "bounds.constant", 1.0);
        float ratCns = (float) config.getDouble(
            "numeric-ratios.constant", 1.0);
        float ratDist = (float) config.getDouble(
            "numeric-ratios.distance", 1.0);
        float ratDistSqr = (float) config.getDouble(
            "numeric-ratios.distance-sqr", 1.0);
        float tot = Math.abs(ratCns + ratDist + ratDistSqr);
        assert tot > 0.0;

        final float cns = ratCns / tot;
        final float dist = ratDist / tot;
        final float distSqr = ratDistSqr / tot;

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

    private static Predicate<Entity> defineEntityPredicate(YamlConfiguration config) {
        boolean players = config.getBoolean("enable.players", true);
        boolean villagers = config.getBoolean("enable.villagers", true);
        boolean monsters = config.getBoolean("enable.enemies", true);
        boolean animals = config.getBoolean("enable.enemies", true);
        boolean vehicles = config.getBoolean("enable.enemies", true);
        return entity ->
            (players && entity instanceof GlowPlayer)
            || (animals && entity instanceof Animals)
            || (monsters && entity instanceof Monster)
            || (vehicles && entity instanceof Vehicle)
            || (villagers && entity instanceof GlowVillager);
    }

    private static Function<Message,Float> defineWeighFunction(YamlConfiguration config) {
        float movementWeightConstant = (float) config.getDouble(
            "message-type.movement.constant", 0.002);
        float movementWeightDistance = (float) config.getDouble(
            "message-type.movement.distance", 0.0001);
        float headRotationWeight = (float) config.getDouble(
            "message-type.head-rotation", 0.2);
        float movementWeightRotation = (float) config.getDouble(
            "message-type.rotation", 0.4);
        float movementWeightTeleport = (float) config.getDouble(
            "message-type.teleport", 20.0);

        return message -> {
            if (message instanceof EntityHeadRotationMessage) {
                return headRotationWeight;
            } else if (message instanceof RelativeEntityPositionRotationMessage) {
                if (movementWeightDistance == 0.0) {
                    return movementWeightConstant;
                }
                RelativeEntityPositionRotationMessage cast =
                    (RelativeEntityPositionRotationMessage) message;
                return (float) (cast.getDeltaX() + cast.getDeltaY() + cast.getDeltaZ())
                    * movementWeightDistance
                    + (movementWeightConstant + movementWeightRotation);
            } else if (message instanceof RelativeEntityPositionMessage) {
                if (movementWeightDistance == 0.0) {
                    return movementWeightConstant;
                }
                RelativeEntityPositionRotationMessage cast =
                    (RelativeEntityPositionRotationMessage) message;
                return (float) (cast.getDeltaX() + cast.getDeltaY() + cast.getDeltaZ())
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
