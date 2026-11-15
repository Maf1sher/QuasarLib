package mafisher.quasar.client.effect.inspect;

import java.util.UUID;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Bounded, render-thread-confined inspector snapshot. Slots are mutable and reused across frames;
 * counts decide which slots are live, so a shrinking effect set never exposes stale entries. Capture
 * is skipped entirely while the inspector is disabled.
 */
@ApiStatus.Internal
@SuppressWarnings({"PMD.TooManyFields", "PMD.TooManyMethods"})
public final class InspectFrame {
    public static final int MAX_EFFECTS = 128;
    public static final int MAX_SCREENS = 64;
    public static final int MAX_AUDIO_PLANS = 32;
    public static final int MAX_AUDIO_CUES = 64;
    public static final int MAX_DRAWS = 16;
    public static final int MAX_ROWS = InspectBufferThumbnail.MAX_ROWS;
    public static final int FLOATS_PER_ROW = InspectBufferThumbnail.FLOATS_PER_ROW;

    private final EffectSlot[] effects = new EffectSlot[MAX_EFFECTS];
    private final ScreenSlot[] screens = new ScreenSlot[MAX_SCREENS];
    private final AudioPlanSlot[] audioPlans = new AudioPlanSlot[MAX_AUDIO_PLANS];
    private final AudioCueSlot[] audioCues = new AudioCueSlot[MAX_AUDIO_CUES];
    private final DrawSlot[] draws = new DrawSlot[MAX_DRAWS];
    private int effectCount;
    private int screenCount;
    private int audioPlanCount;
    private int audioCueCount;
    private int drawCount;
    private long gameTime;
    private long tick;
    private long capturedNanos;
    private int standaloneEffects;
    private int segmentedGroups;
    private int particleDraws;
    private int debrisPieces;

    public InspectFrame() {
        for (int index = 0; index < this.effects.length; index++) {
            this.effects[index] = new EffectSlot();
        }
        for (int index = 0; index < this.screens.length; index++) {
            this.screens[index] = new ScreenSlot();
        }
        for (int index = 0; index < this.audioPlans.length; index++) {
            this.audioPlans[index] = new AudioPlanSlot();
        }
        for (int index = 0; index < this.audioCues.length; index++) {
            this.audioCues[index] = new AudioCueSlot();
        }
        for (int index = 0; index < this.draws.length; index++) {
            this.draws[index] = new DrawSlot();
        }
    }

    /** Starts a capture; counts reset while slot storage stays allocated. */
    public void begin(long gameTime, long tick, long capturedNanos) {
        begin(gameTime, tick, capturedNanos, true);
    }

    /**
     * Starts a capture. Audio slots are kept between HUD refresh ticks so a 5 Hz audio snapshot is
     * not erased by every render frame.
     */
    public void begin(long gameTime, long tick, long capturedNanos, boolean resetAudio) {
        this.gameTime = gameTime;
        this.tick = tick;
        this.capturedNanos = capturedNanos;
        this.effectCount = 0;
        this.screenCount = 0;
        this.drawCount = 0;
        if (resetAudio) {
            this.audioPlanCount = 0;
            this.audioCueCount = 0;
        }
    }

    public void clear() {
        begin(0L, 0L, 0L, true);
    }

    public EffectSlot addEffect() {
        if (this.effectCount >= MAX_EFFECTS) {
            return null;
        }
        EffectSlot slot = this.effects[this.effectCount++];
        slot.reset();
        return slot;
    }

    public ScreenSlot addScreen() {
        if (this.screenCount >= MAX_SCREENS) {
            return null;
        }
        ScreenSlot slot = this.screens[this.screenCount++];
        slot.reset();
        return slot;
    }

    /** Clears screen slots before the composition pass fills them. */
    public void resetScreens() {
        this.screenCount = 0;
    }

    public AudioPlanSlot addAudioPlan() {
        if (this.audioPlanCount >= MAX_AUDIO_PLANS) {
            return null;
        }
        AudioPlanSlot slot = this.audioPlans[this.audioPlanCount++];
        slot.reset();
        return slot;
    }

    public AudioCueSlot addAudioCue() {
        if (this.audioCueCount >= MAX_AUDIO_CUES) {
            return null;
        }
        AudioCueSlot slot = this.audioCues[this.audioCueCount++];
        slot.reset();
        return slot;
    }

    public DrawSlot addDraw() {
        if (this.drawCount >= MAX_DRAWS) {
            return null;
        }
        DrawSlot slot = this.draws[this.drawCount++];
        slot.reset();
        return slot;
    }

    public int effectCount() {
        return this.effectCount;
    }

    public int screenCount() {
        return this.screenCount;
    }

    public int audioPlanCount() {
        return this.audioPlanCount;
    }

    public int audioCueCount() {
        return this.audioCueCount;
    }

    public int drawCount() {
        return this.drawCount;
    }

    public EffectSlot effect(int index) {
        return this.effects[index];
    }

    public ScreenSlot screen(int index) {
        return this.screens[index];
    }

    public AudioPlanSlot audioPlan(int index) {
        return this.audioPlans[index];
    }

    public AudioCueSlot audioCue(int index) {
        return this.audioCues[index];
    }

    public DrawSlot draw(int index) {
        return this.draws[index];
    }

    public long gameTime() {
        return this.gameTime;
    }

    public long tick() {
        return this.tick;
    }

    public long capturedNanos() {
        return this.capturedNanos;
    }

    /** Stores aggregate counts captured around the effect pass. */
    public void counts(int standaloneEffects, int segmentedGroups, int particleDraws, int debrisPieces) {
        this.standaloneEffects = standaloneEffects;
        this.segmentedGroups = segmentedGroups;
        this.particleDraws = particleDraws;
        this.debrisPieces = debrisPieces;
    }

    public int standaloneEffects() {
        return this.standaloneEffects;
    }

    public int segmentedGroups() {
        return this.segmentedGroups;
    }

    public int particleDraws() {
        return this.particleDraws;
    }

    public int debrisPieces() {
        return this.debrisPieces;
    }

    public void debrisPieces(int debrisPieces) {
        this.debrisPieces = debrisPieces;
    }

    /** One live visual effect or segment cell. */
    @SuppressWarnings("PMD.TooManyFields")
    public static final class EffectSlot {
        public UUID instanceId;
        public ResourceLocation effectId;
        public boolean visible;
        public boolean segmented;
        public boolean depthCulled;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float roll;
        public float ageTicks;
        public float progress;
        public float scale;
        public double minX;
        public double minY;
        public double minZ;
        public double maxX;
        public double maxY;
        public double maxZ;
        public double spatialRadius = Double.POSITIVE_INFINITY;
        public int lodLevel;
        public int lodPreviousLevel;
        public double lodBlend = 1.0;
        public double cameraDistance;
        public double gapInRadii;
        public EffectQualityTier quality = EffectQualityTier.HIGH;
        public EffectQualityTier effectiveQuality = EffectQualityTier.HIGH;
        public double coverage;
        public double centreDistance;
        public double score;
        public boolean projected;
        public int passCount;
        public int instanceCount;
        public int particleCount;

        void reset() {
            this.instanceId = null;
            this.effectId = null;
            this.visible = false;
            this.segmented = false;
            this.depthCulled = false;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.yaw = 0.0F;
            this.pitch = 0.0F;
            this.roll = 0.0F;
            this.ageTicks = 0.0F;
            this.progress = 0.0F;
            this.scale = 1.0F;
            this.minX = 0.0;
            this.minY = 0.0;
            this.minZ = 0.0;
            this.maxX = 0.0;
            this.maxY = 0.0;
            this.maxZ = 0.0;
            this.spatialRadius = Double.POSITIVE_INFINITY;
            this.lodLevel = 0;
            this.lodPreviousLevel = 0;
            this.lodBlend = 1.0;
            this.cameraDistance = 0.0;
            this.gapInRadii = 0.0;
            this.quality = EffectQualityTier.HIGH;
            this.effectiveQuality = EffectQualityTier.HIGH;
            this.coverage = 0.0;
            this.centreDistance = 0.0;
            this.score = 0.0;
            this.projected = false;
            this.passCount = 0;
            this.instanceCount = 0;
            this.particleCount = 0;
        }
    }

    /** One screen-effect marker. */
    public static final class ScreenSlot {
        public UUID ownerId;
        public String operationId = "";
        public float red;
        public float green;
        public float blue;
        public float strength;
        public double x;
        public double y;
        public double z;
        public int frameCount;
        public double nearestDistance;

        void reset() {
            this.ownerId = null;
            this.operationId = "";
            this.red = 0.0F;
            this.green = 0.0F;
            this.blue = 0.0F;
            this.strength = 0.0F;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.frameCount = 0;
            this.nearestDistance = 0.0;
        }
    }

    /** One active audio plan. */
    public static final class AudioPlanSlot {
        public UUID effectId;
        public int cueCount;
        public int triggerCount;
        public int activeLoops;
        public int activeInstances;
        public int nextCueIndex;
        public long startGameTime;
        public int durationTicks;
        public int ageTicks;
        public double centerX;
        public double centerY;
        public double centerZ;
        public double translationX;
        public double translationY;
        public double translationZ;

        void reset() {
            this.effectId = null;
            this.cueCount = 0;
            this.triggerCount = 0;
            this.activeLoops = 0;
            this.activeInstances = 0;
            this.nextCueIndex = 0;
            this.startGameTime = 0L;
            this.durationTicks = 0;
            this.ageTicks = 0;
            this.centerX = 0.0;
            this.centerY = 0.0;
            this.centerZ = 0.0;
            this.translationX = 0.0;
            this.translationY = 0.0;
            this.translationZ = 0.0;
        }
    }

    /** One directional or distance cue. */
    public static final class AudioCueSlot {
        public UUID effectId;
        public int cueTick;
        public double x;
        public double y;
        public double z;
        public double directionX;
        public double directionY;
        public double directionZ;
        public float innerDegrees;
        public float outerDegrees;
        public float outsideGain;
        public float maxDistance;
        public boolean directional;
        public boolean looping;
        public boolean played;
        public float listenerGain;

        void reset() {
            this.effectId = null;
            this.cueTick = 0;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.directionX = 0.0;
            this.directionY = 0.0;
            this.directionZ = 0.0;
            this.innerDegrees = 0.0F;
            this.outerDegrees = 0.0F;
            this.outsideGain = 0.0F;
            this.maxDistance = 0.0F;
            this.directional = false;
            this.looping = false;
            this.played = false;
            this.listenerGain = 1.0F;
        }
    }

    /** One instanced draw and the first rows of its CPU payload. */
    public static final class DrawSlot {
        public String passId = "";
        public String renderType = "";
        public String mode = "";
        public int instanceCount;
        public int bytes;
        public int rowCount;
        public final float[] rows = new float[MAX_ROWS * FLOATS_PER_ROW];

        void reset() {
            this.passId = "";
            this.renderType = "";
            this.mode = "";
            this.instanceCount = 0;
            this.bytes = 0;
            this.rowCount = 0;
        }
    }
}
