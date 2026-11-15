package mafisher.quasar.client.effect.inspect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.damage.EffectHitboxOutcome;
import mafisher.quasar.effect.network.InspectHitboxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

/** Draws the inspector's depth-tested world gizmos. */
@ApiStatus.Internal
@SuppressWarnings({"PMD.TooManyMethods", "checkstyle:BooleanExpressionComplexity"})
public final class InspectGizmos {
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final int MAX_LABELS = 8;
    private static final double DEFAULT_CONE_LENGTH = 16.0;

    private InspectGizmos() {
    }

    public static void render(RenderLevelStageEvent event, InspectFrame frame) {
        InspectController controller = InspectController.get();
        if (!controller.enabled() || frame.capturedNanos() == 0L) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = InspectStyle.lines();
        VertexConsumer consumer = buffers.getBuffer(type);
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            drawEffects(controller, frame, poseStack, consumer);
            drawScreens(controller, frame, poseStack, consumer);
            drawAudio(controller, frame, poseStack, consumer);
            drawHitboxes(controller, frame, poseStack, consumer);
        } finally {
            poseStack.popPose();
            buffers.endBatch(type);
        }
        if (controller.category(InspectController.Category.BOUNDS)) {
            drawLabels(frame, poseStack, buffers);
        }
    }

    private static void drawEffects(InspectController controller, InspectFrame frame,
        PoseStack poseStack, VertexConsumer consumer) {
        boolean bounds = controller.category(InspectController.Category.BOUNDS);
        boolean radii = controller.category(InspectController.Category.RADII);
        for (int index = 0; index < frame.effectCount(); index++) {
            InspectFrame.EffectSlot effect = frame.effect(index);
            if (!effect.visible) {
                continue;
            }
            if (bounds && finiteBounds(effect)) {
                LevelRenderer.renderLineBox(poseStack, consumer,
                    effect.minX, effect.minY, effect.minZ, effect.maxX, effect.maxY, effect.maxZ,
                    InspectStyle.BOUNDS.red(), InspectStyle.BOUNDS.green(),
                    InspectStyle.BOUNDS.blue(), InspectStyle.BOUNDS.alpha());
            }
            if (radii && Double.isFinite(effect.spatialRadius) && effect.spatialRadius > 0.0) {
                InspectGeometry.sphere(
                    sink(poseStack, consumer, InspectStyle.RADIUS),
                    new InspectGeometry.Point(effect.x, effect.y, effect.z),
                    effect.spatialRadius
                );
            }
        }
    }

    private static void drawScreens(InspectController controller, InspectFrame frame,
        PoseStack poseStack, VertexConsumer consumer) {
        if (!controller.category(InspectController.Category.SCREENS)) {
            return;
        }
        for (int index = 0; index < frame.screenCount(); index++) {
            InspectFrame.ScreenSlot screen = frame.screen(index);
            InspectStyle.Color color = new InspectStyle.Color(
                Math.max(0.2F, screen.red),
                Math.max(0.2F, screen.green),
                Math.max(0.2F, screen.blue),
                Math.max(0.35F, screen.strength)
            );
            InspectGeometry.LineSink sink = sink(poseStack, consumer, color);
            InspectGeometry.box(sink,
                new InspectGeometry.Point(screen.x - 0.4, screen.y - 0.4, screen.z - 0.4),
                new InspectGeometry.Point(screen.x + 0.4, screen.y + 0.4, screen.z + 0.4));
            InspectGeometry.beam(sink,
                new InspectGeometry.Point(screen.x, screen.y, screen.z),
                new InspectGeometry.Point(screen.x, screen.y + 3.0, screen.z), 0.0);
        }
    }

    private static void drawAudio(InspectController controller, InspectFrame frame,
        PoseStack poseStack, VertexConsumer consumer) {
        if (!controller.category(InspectController.Category.AUDIO)) {
            return;
        }
        for (int index = 0; index < frame.audioCueCount(); index++) {
            InspectFrame.AudioCueSlot cue = frame.audioCue(index);
            InspectGeometry.Point source = new InspectGeometry.Point(cue.x, cue.y, cue.z);
            InspectStyle.Color color = cue.played ? InspectStyle.AUDIO_PAST : InspectStyle.AUDIO;
            InspectGeometry.LineSink sink = sink(poseStack, consumer, color);
            if (cue.directional) {
                double length = cue.maxDistance > 0.0F ? cue.maxDistance : DEFAULT_CONE_LENGTH;
                InspectGeometry.cone(sink, source,
                    new InspectGeometry.Point(cue.directionX, cue.directionY, cue.directionZ),
                    length, cue.innerDegrees, cue.outerDegrees);
            } else if (cue.maxDistance > 0.0F) {
                InspectGeometry.circle(sink, source, new InspectGeometry.Point(0.0, 1.0, 0.0),
                    cue.maxDistance);
                InspectGeometry.beam(sink, source,
                    new InspectGeometry.Point(cue.x, cue.y + 2.0, cue.z), 0.0);
            }
        }
    }

    private static void drawHitboxes(InspectController controller, InspectFrame frame,
        PoseStack poseStack, VertexConsumer consumer) {
        if (!controller.category(InspectController.Category.HITBOXES)) {
            return;
        }
        InspectHitboxPayload payload = InspectHitboxStore.current(frame.gameTime());
        if (payload == null) {
            return;
        }
        float fade = InspectHitboxStore.fade(frame.gameTime());
        if (fade <= 0.0F) {
            return;
        }
        for (InspectHitboxPayload.Session session : payload.sessions()) {
            for (InspectHitboxPayload.Shape entry : session.shapes()) {
                InspectStyle.Color base = entry.outcome() == EffectHitboxOutcome.REMOVE
                    ? InspectStyle.HITBOX_REMOVE : InspectStyle.HITBOX_DAMAGE;
                InspectStyle.Color color = new InspectStyle.Color(
                    base.red(), base.green(), base.blue(), base.alpha() * fade);
                drawShape(sink(poseStack, consumer, color), entry.shape());
            }
        }
    }

    private static void drawShape(InspectGeometry.LineSink sink, EffectDamageShape shape) {
        if (shape instanceof EffectDamageShape.Box box) {
            drawBox(sink, box);
        } else if (shape instanceof EffectDamageShape.Sphere sphere) {
            InspectGeometry.sphere(sink, point(sphere.center().x(), sphere.center().y(),
                sphere.center().z()), sphere.radius());
        } else if (shape instanceof EffectDamageShape.Cylinder cylinder) {
            InspectGeometry.cylinder(sink, point(cylinder.base().x(), cylinder.base().y(),
                cylinder.base().z()), cylinder.radius(), cylinder.height());
        } else if (shape instanceof EffectDamageShape.Beam beam) {
            InspectGeometry.beam(sink, point(beam.start().x(), beam.start().y(), beam.start().z()),
                point(beam.end().x(), beam.end().y(), beam.end().z()), beam.radius());
        }
    }

    private static void drawBox(InspectGeometry.LineSink sink, EffectDamageShape.Box box) {
        InspectGeometry.box(sink,
            point(box.bounds().minX(), box.bounds().minY(), box.bounds().minZ()),
            point(box.bounds().maxX(), box.bounds().maxY(), box.bounds().maxZ()));
    }

    private static void drawLabels(InspectFrame frame, PoseStack poseStack,
        MultiBufferSource.BufferSource buffers) {
        int count = Math.min(frame.effectCount(), MAX_LABELS);
        for (int index = 0; index < count; index++) {
            InspectFrame.EffectSlot effect = frame.effect(index);
            if (!effect.visible) {
                continue;
            }
            String label = shortId(effect.instanceId) + " "
                + (effect.effectId == null ? "-" : effect.effectId.getPath());
            double y = finiteBounds(effect) ? effect.maxY + 0.4 : effect.y;
            DebugRenderer.renderFloatingText(
                poseStack, buffers, label, effect.x, y, effect.z, 0xFFFFFFFF
            );
        }
        buffers.endBatch();
    }

    private static InspectGeometry.LineSink sink(PoseStack poseStack, VertexConsumer consumer,
        InspectStyle.Color color) {
        return new VertexSink(poseStack.last().pose(), consumer, color);
    }

    private static InspectGeometry.Point point(double x, double y, double z) {
        return new InspectGeometry.Point(x, y, z);
    }

    private static boolean finiteBounds(InspectFrame.EffectSlot effect) {
        return Double.isFinite(effect.minX) && Double.isFinite(effect.minY)
            && Double.isFinite(effect.minZ) && Double.isFinite(effect.maxX)
            && Double.isFinite(effect.maxY) && Double.isFinite(effect.maxZ);
    }

    private static String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private record VertexSink(Matrix4f matrix, VertexConsumer consumer, InspectStyle.Color color)
        implements InspectGeometry.LineSink {
        @Override
        public void line(double x1, double y1, double z1, double x2, double y2, double z2) {
            vertex(x1, y1, z1);
            vertex(x2, y2, z2);
        }

        private void vertex(double x, double y, double z) {
            this.consumer.addVertex(this.matrix, (float)x, (float)y, (float)z)
                .setColor(this.color.red(), this.color.green(), this.color.blue(), this.color.alpha())
                .setNormal(0.0F, 1.0F, 0.0F);
        }
    }
}
