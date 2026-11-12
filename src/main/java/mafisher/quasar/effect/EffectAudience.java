package mafisher.quasar.effect;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Network delivery policy for a visual effect. */
public sealed interface EffectAudience permits EffectAudience.Nearby, EffectAudience.DimensionWide {
    static EffectAudience nearby(double radius) {
        return new Nearby(radius);
    }

    static EffectAudience dimensionWide() {
        return DimensionWide.INSTANCE;
    }

    /** Adds every player in this audience around {@code center}, expanding nearby radius by {@code padding}. */
    default void collect(ServerLevel level, Vec3 center, double padding, Map<UUID, ServerPlayer> target) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(target, "target");
        for (ServerPlayer player : level.players()) {
            if (includes(level, center, padding, player)) {
                target.put(player.getUUID(), player);
            }
        }
    }

    /** Whether one player belongs to this audience around {@code center}. */
    boolean includes(ServerLevel level, Vec3 center, double padding, ServerPlayer player);

    record Nearby(double radius) implements EffectAudience {
        public Nearby {
            if (!Double.isFinite(radius) || radius < 0.0 || radius > 512.0) {
                throw new IllegalArgumentException("Effect audience radius must be between 0 and 512 blocks");
            }
        }

        @Override
        public boolean includes(ServerLevel level, Vec3 center, double padding, ServerPlayer player) {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(player, "player");
            double expandedRadius = this.radius + Math.max(0.0, padding);
            double radiusSqr = expandedRadius * expandedRadius;
            return player.level() == level
                && center.distanceToSqr(player.position()) <= radiusSqr;
        }
    }

    final class DimensionWide implements EffectAudience {
        private static final DimensionWide INSTANCE = new DimensionWide();

        private DimensionWide() {
        }

        @Override
        public void collect(ServerLevel level, Vec3 center, double padding, Map<UUID, ServerPlayer> target) {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(target, "target");
            for (ServerPlayer player : level.players()) {
                target.put(player.getUUID(), player);
            }
        }

        @Override
        public boolean includes(ServerLevel level, Vec3 center, double padding, ServerPlayer player) {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(player, "player");
            return player.level() == level;
        }
    }
}
