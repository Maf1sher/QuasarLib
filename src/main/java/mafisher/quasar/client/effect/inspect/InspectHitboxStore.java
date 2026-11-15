package mafisher.quasar.client.effect.inspect;

import mafisher.quasar.effect.network.InspectHitboxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/** Client-side, expiring store for the operator-authored hitbox snapshot. */
@ApiStatus.Internal
public final class InspectHitboxStore {
    public static final int TTL_TICKS = 200;

    private static InspectHitboxPayload snapshot;
    private static long receivedTick;

    private InspectHitboxStore() {
    }

    /** Accepts one server snapshot for the current dimension; older snapshots are replaced. */
    public static void accept(InspectHitboxPayload payload) {
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        snapshot = payload;
        receivedTick = level.getGameTime();
    }

    /** Returns the live snapshot, or {@code null} after the TTL or a dimension change. */
    public static @Nullable InspectHitboxPayload current(long gameTime) {
        if (snapshot == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null || !level.dimension().location().equals(snapshot.dimensionId())) {
            return null;
        }
        long age = gameTime - receivedTick;
        return age < 0L || age > TTL_TICKS ? null : snapshot;
    }

    /** Returns one bounded HUD status line. */
    public static String status(long gameTime) {
        if (snapshot == null) {
            return "hitboxes: run /quasarlib hitboxes (op)";
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null || !level.dimension().location().equals(snapshot.dimensionId())) {
            return "hitboxes: other dimension";
        }
        long age = gameTime - receivedTick;
        if (age < 0L || age > TTL_TICKS) {
            return "hitboxes: expired";
        }
        return "hitboxes: " + snapshot.sessions().size() + " session(s), " + age + "/" + TTL_TICKS + "t";
    }

    /** Returns the fade factor for the live snapshot, or zero when it has expired. */
    public static float fade(long gameTime) {
        if (snapshot == null) {
            return 0.0F;
        }
        long remaining = TTL_TICKS - (gameTime - receivedTick);
        if (remaining <= 0L) {
            return 0.0F;
        }
        return Math.min(1.0F, remaining / 40.0F);
    }

    public static void clear() {
        snapshot = null;
        receivedTick = 0L;
    }
}
