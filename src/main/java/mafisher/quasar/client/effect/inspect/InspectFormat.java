package mafisher.quasar.client.effect.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mafisher.quasar.client.effect.quality.ClientQualityController;
import org.jetbrains.annotations.ApiStatus;

/** Bounded text output shared by the inspector HUD and the status command. */
@ApiStatus.Internal
@SuppressWarnings("PMD.TooManyMethods")
public final class InspectFormat {
    public static final int MAX_STATUS_EFFECTS = 8;
    public static final int MAX_HUD_EFFECTS = 6;
    public static final int MAX_STATUS_AUDIO = 8;
    public static final int MAX_HUD_AUDIO = 2;
    public static final int MAX_STATUS_DRAWS = 8;
    public static final int MAX_HUD_DRAWS = 2;
    public static final int MAX_STATUS_SCREENS = 4;
    private static final int MAX_ID_CHARS = 36;

    private InspectFormat() {
    }

    /** Full bounded status report for `/quasarlib inspect status`. */
    public static List<String> statusLines(InspectFrame frame) {
        List<String> lines = new ArrayList<>();
        if (frame.capturedNanos() == 0L) {
            lines.add("Inspector is on; waiting for a rendered frame.");
            return lines;
        }
        lines.add(summary(frame));
        lines.add(qualityLine());
        int effects = Math.min(frame.effectCount(), MAX_STATUS_EFFECTS);
        for (int index = 0; index < effects; index++) {
            InspectFrame.EffectSlot effect = frame.effect(index);
            lines.add(effectLine(index + 1, effect));
            lines.add("  bounds=" + bounds(effect) + " depth=" + (effect.depthCulled ? "culled" : "ok"));
        }
        if (frame.effectCount() > effects) {
            lines.add("  ... +" + (frame.effectCount() - effects) + " effect(s)");
        }
        appendAudio(lines, frame, MAX_STATUS_AUDIO);
        appendAudioCones(lines, frame, MAX_STATUS_AUDIO);
        appendDraws(lines, frame, MAX_STATUS_DRAWS);
        appendScreens(lines, frame);
        lines.add(InspectHitboxStore.status(frame.gameTime()));
        return lines;
    }

    /** Condensed HUD lines; the buffer thumbnail is drawn separately. */
    public static List<String> hudLines(InspectFrame frame) {
        List<String> lines = new ArrayList<>();
        if (frame.capturedNanos() == 0L) {
            lines.add("inspect: waiting for a frame");
            return lines;
        }
        lines.add(hudSummary(frame));
        lines.add(qualityLine());
        int effects = Math.min(frame.effectCount(), MAX_HUD_EFFECTS);
        for (int index = 0; index < effects; index++) {
            lines.add(hudEffectLine(index + 1, frame.effect(index)));
        }
        if (frame.effectCount() > effects) {
            lines.add("  ... +" + (frame.effectCount() - effects) + " effect(s)");
        }
        appendAudio(lines, frame, MAX_HUD_AUDIO);
        appendDraws(lines, frame, MAX_HUD_DRAWS);
        lines.add(InspectHitboxStore.status(frame.gameTime()));
        return lines;
    }

    private static String hudSummary(InspectFrame frame) {
        return "inspect fx=" + frame.effectCount() + "/" + InspectFrame.MAX_EFFECTS
            + " seg=" + frame.segmentedGroups()
            + " scr=" + frame.screenCount()
            + " aud=" + frame.audioPlanCount()
            + " draw=" + frame.drawCount()
            + " part=" + frame.particleDraws()
            + " deb=" + frame.debrisPieces();
    }

    private static String summary(InspectFrame frame) {
        return "inspect tick=" + frame.tick()
            + " fx=" + frame.effectCount() + "/" + InspectFrame.MAX_EFFECTS
            + " standalone=" + frame.standaloneEffects()
            + " seg=" + frame.segmentedGroups()
            + " screens=" + frame.screenCount()
            + " audio=" + frame.audioPlanCount()
            + " draws=" + frame.drawCount()
            + " particles=" + frame.particleDraws()
            + " debris=" + frame.debrisPieces();
    }

    private static String qualityLine() {
        ClientQualityController.Telemetry telemetry = ClientQualityController.telemetry();
        return "frame tier=" + telemetry.tier()
            + (telemetry.automatic() ? " auto" : " fixed")
            + " cpu=" + millis(telemetry.cpuNanos())
            + " gpu=" + (telemetry.hasGpuSample() ? millis(telemetry.gpuNanos()) : "n/a")
            + " p95=" + millis(telemetry.p95Nanos()) + "/" + millis(telemetry.budgetNanos());
    }

    private static String effectLine(int index, InspectFrame.EffectSlot effect) {
        return "#" + index + " " + shortId(effect.instanceId)
            + " " + trim(effect.effectId == null ? "-" : effect.effectId.toString(), MAX_ID_CHARS)
            + " age=" + Math.round(effect.ageTicks)
            + " p=" + decimal(effect.progress)
            + " L" + effect.lodLevel + "/" + effect.lodPreviousLevel
            + " q=" + effect.effectiveQuality + "/" + effect.quality
            + " cov=" + decimal(effect.coverage)
            + " score=" + decimal(effect.score)
            + " d=" + decimal(effect.cameraDistance)
            + " r=" + (Double.isFinite(effect.spatialRadius) ? decimal(effect.spatialRadius) : "inf")
            + " pass=" + effect.passCount
            + " inst=" + effect.instanceCount
            + " part=" + effect.particleCount
            + (effect.visible ? "" : " hidden")
            + (effect.segmented ? " seg" : "");
    }

    private static String hudEffectLine(int index, InspectFrame.EffectSlot effect) {
        return "#" + index + " " + shortId(effect.instanceId)
            + " " + trim(effect.effectId == null ? "-" : effect.effectId.getPath(), 20)
            + " age=" + Math.round(effect.ageTicks)
            + " L" + effect.lodLevel
            + " q=" + effect.effectiveQuality
            + " cov=" + decimal(effect.coverage)
            + " d=" + decimal(effect.cameraDistance)
            + " i=" + effect.instanceCount
            + (effect.depthCulled ? " culled" : "");
    }

    private static String bounds(InspectFrame.EffectSlot effect) {
        return "(" + decimal(effect.minX) + "," + decimal(effect.minY) + "," + decimal(effect.minZ)
            + ")-(" + decimal(effect.maxX) + "," + decimal(effect.maxY) + "," + decimal(effect.maxZ) + ")";
    }

    private static void appendAudio(List<String> lines, InspectFrame frame, int limit) {
        int count = Math.min(frame.audioPlanCount(), limit);
        for (int index = 0; index < count; index++) {
            InspectFrame.AudioPlanSlot plan = frame.audioPlan(index);
            lines.add("audio " + shortId(plan.effectId)
                + " cues=" + plan.nextCueIndex + "/" + plan.cueCount
                + " trig=" + plan.triggerCount
                + " loops=" + plan.activeLoops
                + " active=" + plan.activeInstances
                + " age=" + plan.ageTicks + "/" + plan.durationTicks);
        }
        if (frame.audioPlanCount() > count) {
            lines.add("  ... +" + (frame.audioPlanCount() - count) + " audio plan(s)");
        }
    }

    private static void appendAudioCones(List<String> lines, InspectFrame frame, int limit) {
        int count = 0;
        for (int index = 0; index < frame.audioCueCount() && count < limit; index++) {
            InspectFrame.AudioCueSlot cue = frame.audioCue(index);
            if (!cue.directional) {
                continue;
            }
            lines.add("  cone " + shortId(cue.effectId)
                + " dir=" + decimal(cue.directionX) + "," + decimal(cue.directionY)
                + "," + decimal(cue.directionZ)
                + " angles=" + Math.round(cue.innerDegrees)
                + "/" + Math.round(cue.outerDegrees)
                + " max=" + decimal(cue.maxDistance)
                + " gain=" + decimal(cue.listenerGain));
            count++;
        }
    }

    private static void appendDraws(List<String> lines, InspectFrame frame, int limit) {
        int count = Math.min(frame.drawCount(), limit);
        for (int index = 0; index < count; index++) {
            InspectFrame.DrawSlot draw = frame.draw(index);
            lines.add("draw #" + index
                + " pass=" + trim(draw.passId, 24)
                + " type=" + trim(draw.renderType, 24)
                + " inst=" + draw.instanceCount
                + " bytes=" + draw.bytes
                + " mode=" + draw.mode
                + " rows=" + draw.rowCount);
        }
        if (frame.drawCount() > count) {
            lines.add("  ... +" + (frame.drawCount() - count) + " draw(s)");
        }
    }

    private static void appendScreens(List<String> lines, InspectFrame frame) {
        int count = Math.min(frame.screenCount(), MAX_STATUS_SCREENS);
        for (int index = 0; index < count; index++) {
            InspectFrame.ScreenSlot screen = frame.screen(index);
            lines.add("screen " + shortId(screen.ownerId)
                + " op=" + trim(screen.operationId, 32)
                + " frames=" + screen.frameCount
                + " mask=" + decimal(screen.strength)
                + " nearest=" + decimal(screen.nearestDistance));
        }
        if (frame.screenCount() > count) {
            lines.add("  ... +" + (frame.screenCount() - count) + " screen batch(es)");
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value)) {
            return "inf";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2fms", nanos / 1_000_000.0D);
    }
}
