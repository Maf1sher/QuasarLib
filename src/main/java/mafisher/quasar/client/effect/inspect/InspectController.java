package mafisher.quasar.client.effect.inspect;

import java.util.EnumSet;
import java.util.List;
import mafisher.quasar.client.effect.audio.ClientEffectAudio;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Client-local inspector state, category toggles, and bounded capture cadence. */
@ApiStatus.Internal
@SuppressWarnings("PMD.TooManyMethods")
public final class InspectController {
    public static final int REFRESH_TICKS = 4;

    private static final InspectController INSTANCE = new InspectController();

    private final InspectFrame frame = new InspectFrame();
    private final EnumSet<Category> categories = EnumSet.allOf(Category.class);
    private boolean enabled;
    private boolean refreshing;
    private long lastRefreshTick = Long.MIN_VALUE;
    private int selectedDraw;
    private List<String> statusLines = List.of();
    private List<String> hudLines = List.of();

    private InspectController() {
    }

    public static InspectController get() {
        return INSTANCE;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        resetData();
    }

    public boolean category(Category category) {
        return this.categories.contains(category);
    }

    public boolean toggleCategory(Category category) {
        if (!this.categories.remove(category)) {
            this.categories.add(category);
            return true;
        }
        return false;
    }

    public InspectFrame frame() {
        return this.frame;
    }

    public List<String> statusLines() {
        return this.statusLines;
    }

    public List<String> hudLines() {
        return this.hudLines;
    }

    public int selectedDraw() {
        if (this.frame.drawCount() == 0) {
            return -1;
        }
        return Math.min(this.selectedDraw, this.frame.drawCount() - 1);
    }

    public boolean selectDraw(int index) {
        if (index < 0 || index >= this.frame.drawCount()) {
            return false;
        }
        this.selectedDraw = index;
        return true;
    }

    /** Starts one capture and returns the reused frame. */
    public InspectFrame beginFrame(long gameTime, long tick, long capturedNanos) {
        this.refreshing = this.lastRefreshTick == Long.MIN_VALUE
            || tick - this.lastRefreshTick >= REFRESH_TICKS;
        this.frame.begin(gameTime, tick, capturedNanos, this.refreshing);
        return this.frame;
    }

    /** Captures audio plans only on refresh ticks; the slots survive non-refresh frames. */
    public void captureAudio() {
        if (!this.refreshing) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 listener;
        if (camera.isInitialized()) {
            listener = camera.getPosition();
        } else if (minecraft.player != null) {
            listener = minecraft.player.getEyePosition();
        } else {
            listener = Vec3.ZERO;
        }
        ClientEffectAudio.forEachDiagnostic(diagnostic ->
            InspectAudioCapture.capture(this.frame, diagnostic, listener));
    }

    /** Rebuilds bounded text only on refresh ticks. */
    public void finishFrame() {
        if (!this.refreshing) {
            return;
        }
        this.lastRefreshTick = this.frame.tick();
        this.statusLines = InspectFormat.statusLines(this.frame);
        this.hudLines = InspectFormat.hudLines(this.frame);
    }

    /** Drops captured data but keeps the inspector enabled and its categories. */
    public void resetData() {
        this.frame.clear();
        this.statusLines = List.of();
        this.hudLines = List.of();
        this.lastRefreshTick = Long.MIN_VALUE;
        this.selectedDraw = 0;
    }

    /** Disables the inspector and drops every client-side snapshot. */
    public void clear() {
        this.enabled = false;
        resetData();
        InspectHitboxStore.clear();
    }

    /** Inspector categories; each one controls both its gizmo and its HUD section. */
    public enum Category {
        BOUNDS,
        RADII,
        AUDIO,
        BUFFERS,
        SCREENS,
        HITBOXES
    }
}
