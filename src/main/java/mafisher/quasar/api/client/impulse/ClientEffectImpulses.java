package mafisher.quasar.api.client.impulse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.QuasarClientConfig;
import mafisher.quasar.api.client.screen.ScreenEffectOperationRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side entry point for distance-attenuated camera shake and additive
 * screen-flash impulses.
 *
 * <p>Call these methods from client code, for example from a visual renderer or
 * a {@link ScreenEffectOperationRenderer}. Impulses are presentation-only: they
 * never change player rotation, aim, damage, or server state. Excess impulses
 * are dropped oldest-first instead of throwing, so a busy scene degrades
 * gracefully. {@link #clear()} runs on level unload and disconnect.</p>
 */
public final class ClientEffectImpulses {
    public static final int MAX_SHAKE_IMPULSES = 16;
    public static final int MAX_FLASH_IMPULSES = 8;
    public static final float MAX_TOTAL_ANGLE_DEGREES = 30.0F;
    private static final float MAX_TOTAL_FLASH_STRENGTH = 1.0F;

    private static final Object LOCK = new Object();
    private static final List<ActiveShake> SHAKES = new ArrayList<>();
    private static final List<ActiveFlash> FLASHES = new ArrayList<>();

    private ClientEffectImpulses() {
    }

    /** Queues one camera-shake impulse, dropping the oldest when the cap is reached. */
    public static void cameraShake(CameraShakeImpulse impulse) {
        Objects.requireNonNull(impulse, "impulse");
        if (!QuasarClientConfig.cinematicImpulses()) {
            return;
        }
        synchronized (LOCK) {
            if (SHAKES.size() >= MAX_SHAKE_IMPULSES) {
                SHAKES.remove(0);
            }
            SHAKES.add(new ActiveShake(impulse));
        }
    }

    /** Queues one screen-flash impulse, dropping the oldest when the cap is reached. */
    public static void screenFlash(ScreenFlashImpulse impulse) {
        Objects.requireNonNull(impulse, "impulse");
        if (!QuasarClientConfig.cinematicImpulses()) {
            return;
        }
        synchronized (LOCK) {
            if (FLASHES.size() >= MAX_FLASH_IMPULSES) {
                FLASHES.remove(0);
            }
            FLASHES.add(new ActiveFlash(impulse));
        }
    }

    /** Removes every active impulse. */
    public static void clear() {
        synchronized (LOCK) {
            SHAKES.clear();
            FLASHES.clear();
        }
    }

    public static void tick() {
        synchronized (LOCK) {
            for (int index = SHAKES.size() - 1; index >= 0; index--) {
                ActiveShake shake = SHAKES.get(index);
                shake.ageTicks++;
                if (shake.ageTicks >= shake.impulse.durationTicks()) {
                    SHAKES.remove(index);
                }
            }
            for (int index = FLASHES.size() - 1; index >= 0; index--) {
                ActiveFlash flash = FLASHES.get(index);
                flash.ageTicks++;
                if (flash.ageTicks >= flash.impulse.durationTicks()) {
                    FLASHES.remove(index);
                }
            }
        }
    }

    public static boolean hasShake() {
        synchronized (LOCK) {
            return !SHAKES.isEmpty();
        }
    }

    public static boolean hasFlash() {
        synchronized (LOCK) {
            return !FLASHES.isEmpty();
        }
    }

    /** Sums every active shake into per-axis degree offsets, clamped for comfort. */
    public static Vector3f shakeOffsets(Vec3 cameraPosition, float partialTick) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        synchronized (LOCK) {
            float yaw = 0.0F;
            float pitch = 0.0F;
            float roll = 0.0F;
            for (ActiveShake shake : SHAKES) {
                Vector3f offset = shake.impulse.angleOffsetsDegrees(
                    cameraPosition, shake.ageTicks + partialTick);
                yaw += offset.x;
                pitch += offset.y;
                roll += offset.z;
            }
            return new Vector3f(
                clamp(yaw, MAX_TOTAL_ANGLE_DEGREES),
                clamp(pitch, MAX_TOTAL_ANGLE_DEGREES),
                clamp(roll, MAX_TOTAL_ANGLE_DEGREES)
            );
        }
    }

    /** Samples a weighted average color and the strongest combined flash strength. */
    public static FlashSample sampleFlash(Vec3 cameraPosition, float partialTick) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        synchronized (LOCK) {
            float total = 0.0F;
            float red = 0.0F;
            float green = 0.0F;
            float blue = 0.0F;
            for (ActiveFlash flash : FLASHES) {
                float strength = flash.impulse.strengthAt(
                    cameraPosition, flash.ageTicks + partialTick);
                if (strength <= 0.0F) {
                    continue;
                }
                total += strength;
                red += flash.impulse.red() * strength;
                green += flash.impulse.green() * strength;
                blue += flash.impulse.blue() * strength;
            }
            if (total <= 0.0F) {
                return FlashSample.NONE;
            }
            float scale = 1.0F / total;
            return new FlashSample(
                red * scale, green * scale, blue * scale,
                Math.min(total, MAX_TOTAL_FLASH_STRENGTH)
            );
        }
    }

    public static int activeShakeCount() {
        synchronized (LOCK) {
            return SHAKES.size();
        }
    }

    public static int activeFlashCount() {
        synchronized (LOCK) {
            return FLASHES.size();
        }
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    public record FlashSample(float red, float green, float blue, float strength) {
        public static final FlashSample NONE = new FlashSample(1.0F, 1.0F, 1.0F, 0.0F);
    }

    private static final class ActiveShake {
        private final CameraShakeImpulse impulse;
        private int ageTicks;

        private ActiveShake(CameraShakeImpulse impulse) {
            this.impulse = impulse;
        }
    }

    private static final class ActiveFlash {
        private final ScreenFlashImpulse impulse;
        private int ageTicks;

        private ActiveFlash(ScreenFlashImpulse impulse) {
            this.impulse = impulse;
        }
    }
}
