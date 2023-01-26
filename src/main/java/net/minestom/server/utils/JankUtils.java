package net.minestom.server.utils;

import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.instance.Chunk;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.player.PlayerUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.MinecraftServer.TICK_PER_SECOND;

public class JankUtils {

    public static double lerp(double v1, double v2, double amount) {
        return (v2 - v1) * amount + v1;
    }

    @Contract(pure = true)
    public static @NotNull Pos lerpPos(@NotNull Pos oldPos, @NotNull Pos newPos, double amount) {
        return oldPos.apply((x, y, z, yaw, pitch) -> new Pos(
                (float) lerp(x, newPos.x(), amount),
                (float) lerp(y, newPos.y(), amount),
                (float) lerp(z, newPos.z(), amount),
                (float) lerp(yaw, newPos.yaw(), amount),
                (float) lerp(pitch, newPos.pitch(), amount)
        ));
    }


    public static Pos simulatePositionUpdate(@NotNull Entity entity) {
        Pos nextPos = entity.getPosition();
        if (entity.getVehicle() != null) return nextPos;

        final boolean noGravity = entity.hasNoGravity();
        final boolean hasVelocity = entity.hasVelocity();
        if (!hasVelocity && noGravity) {
            return nextPos;
        }
        final float tps = TICK_PER_SECOND;
        final Vec currentVelocity = entity.getVelocity();
        final Vec deltaPos = currentVelocity.div(tps);

        final Pos newPosition;
        final Vec newVelocity;
        if (entity.hasPhysics()) {
            final var physicsResult = CollisionUtils.handlePhysics(entity, deltaPos, entity.getLastPhysicsResult());

            newPosition = physicsResult.newPosition();
            newVelocity = physicsResult.newVelocity();
        } else {
            newVelocity = deltaPos;
            newPosition = entity.getPosition().add(deltaPos);
        }

        // World border collision
        final Pos finalVelocityPosition = CollisionUtils.applyWorldBorder(entity.getInstance(), entity.getPosition(), newPosition);
        final boolean positionChanged = !finalVelocityPosition.samePoint(entity.getPosition());
        final boolean isPlayer = entity instanceof Player;
        if (!positionChanged) return nextPos;
        else if (hasVelocity || newVelocity.isZero()) return nextPos;
        final Chunk finalChunk = ChunkUtils.retrieve(entity.getInstance(), entity.getChunk(), finalVelocityPosition);
        if (!ChunkUtils.isLoaded(finalChunk)) {
            // Entity shouldn't be updated when moving in an unloaded chunk
            return nextPos;
        }

        if (positionChanged) {
            if (entity.getEntityType() == EntityType.ITEM || entity.getEntityType() == EntityType.FALLING_BLOCK) {
                // TODO find other exceptions
                nextPos = finalVelocityPosition;
            } else {
                if (!PlayerUtils.isSocketClient(entity)) {
                    nextPos = finalVelocityPosition;
                }
            }
        }

        return nextPos;
    }
}
