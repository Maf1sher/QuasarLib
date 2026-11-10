package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.effect.EffectDebrisAttractor;
import mafisher.quasar.effect.EffectDebrisMath;
import mafisher.quasar.effect.EffectDebrisSpawn;
import mafisher.quasar.effect.network.EffectDebrisPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight client-only block debris. Bursts are simulated from a seed and never collide with
 * terrain; the renderer batches every visible piece into the shared instanced draw path.
 */
final class ClientEffectDebris {
    static final int MAX_ACTIVE_PIECES = 12_288;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final EffectMesh CUBE = createCube();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static GpuMeshCache meshCache;
    private static InstancedBatchRenderer renderer;
    private static boolean renderFailureLogged;

    private ClientEffectDebris() {
    }

    static void accept(EffectDebrisPayload payload) {
        Objects.requireNonNull(payload, "payload");
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        BlockState state = Block.stateById(payload.blockStateId());
        if (state.isAir()) {
            return;
        }
        ClientEffectClock.observe(payload.serverGameTime(), level.getGameTime());
        TextureAtlasSprite sprite = minecraft.getBlockRenderer().getBlockModelShaper()
            .getParticleIcon(state);
        if (sprite == null) {
            return;
        }
        int tint = minecraft.getBlockColors().getColor(
            state, level, BlockPos.containing(payload.origin()), 0
        );
        float red = tint < 0 ? 1.0F : ((tint >> 16) & 0xFF) / 255.0F;
        float green = tint < 0 ? 1.0F : ((tint >> 8) & 0xFF) / 255.0F;
        float blue = tint < 0 ? 1.0F : (tint & 0xFF) / 255.0F;
        Burst burst = new Burst(
            payload.serverGameTime(),
            payload.lifetimeTicks(),
            payload.gravity(),
            sprite.getU0(),
            sprite.getV0(),
            sprite.getU1() - sprite.getU0(),
            sprite.getV1() - sprite.getV0(),
            red,
            green,
            blue,
            payload.attractor(),
            payload.scale(),
            pieces(payload)
        );
        BURSTS.add(burst);
        while (totalPieces() > MAX_ACTIVE_PIECES && BURSTS.size() > 1) {
            BURSTS.removeFirst();
        }
    }

    static void clear() {
        BURSTS.clear();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (meshCache != null) {
            meshCache.close();
            meshCache = null;
        }
        renderFailureLogged = false;
    }

    static void onResourceReload() {
        clear();
    }

    static int activePieces() {
        return totalPieces();
    }

    /** Deterministic piece state for one payload; exposed to headless tests. */
    static List<Piece> pieces(EffectDebrisPayload payload) {
        RandomSource random = RandomSource.create(payload.seed());
        List<Piece> pieces = new ArrayList<>(payload.pieces());
        for (int index = 0; index < payload.pieces(); index++) {
            Vec3 randomDirection = randomDirection(random);
            Vec3 base = payload.direction().lengthSqr() > 1.0E-9
                ? payload.direction().normalize()
                : randomDirection;
            double spread = payload.spread();
            Vec3 direction = base.scale(1.0 - spread).add(randomDirection.scale(spread));
            if (direction.lengthSqr() <= 1.0E-9) {
                direction = randomDirection;
            } else {
                direction = direction.normalize();
            }
            double speed = payload.speed() * (0.45 + random.nextDouble() * 0.55);
            Vec3 origin;
            if (payload.spawn() instanceof EffectDebrisSpawn.Shell shell) {
                origin = shellPoint(random, shell);
            } else if (payload.spawn() instanceof EffectDebrisSpawn.Disk disk) {
                origin = diskPoint(random, disk);
            } else {
                origin = payload.origin().add(
                    (random.nextDouble() * 2.0 - 1.0) * 0.35,
                    (random.nextDouble() * 2.0 - 1.0) * 0.35,
                    (random.nextDouble() * 2.0 - 1.0) * 0.35
                );
            }
            int lifetime = Math.max(1, (int)Math.round(
                payload.lifetimeTicks() * (0.7 + random.nextDouble() * 0.3)
            ));
            pieces.add(new Piece(
                origin,
                direction.scale(speed),
                random.nextFloat() * 360.0F,
                random.nextFloat() * 360.0F,
                random.nextFloat() * 360.0F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                0.075F + random.nextFloat() * 0.075F,
                lifetime
            ));
        }
        return List.copyOf(pieces);
    }

    static void render(RenderLevelStageEvent event) {
        if (BURSTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        long serverTime = ClientEffectClock.estimate(level.getGameTime());
        float partialTick = (float)event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BURSTS.removeIf(burst -> burst.isExpired(serverTime));
        if (BURSTS.isEmpty()) {
            return;
        }

        float qualityScale = switch (ClientQualityController.tier()) {
            case LOW -> 0.5F;
            case MEDIUM -> 0.75F;
            default -> 1.0F;
        };
        Vec3 camera = event.getCamera().getPosition();
        List<InstancedBatchBuilder.Instance> instances = new ArrayList<>();
        for (Burst burst : BURSTS) {
            int visible = Math.max(1, (int)Math.ceil(burst.pieces.size() * qualityScale));
            double age = Math.max(0.0, serverTime - burst.startGameTime + partialTick);
            for (int index = 0; index < visible; index++) {
                Piece piece = burst.pieces.get(index);
                if (age >= piece.lifetimeTicks()) {
                    continue;
                }
                double progress = age / piece.lifetimeTicks();
                double x;
                double y;
                double z;
                float alpha;
                float scale = piece.scale() * burst.scale;
                if (burst.attractor != null) {
                    Vec3 position = EffectDebrisMath.infallPosition(
                        piece.position(), burst.attractor, progress
                    );
                    if (position.distanceTo(burst.attractor.center()) <= burst.attractor.horizonRadius()) {
                        continue;
                    }
                    x = position.x;
                    y = position.y;
                    z = position.z;
                    alpha = (float)(1.0 - smoothstep(0.72, 1.0, progress));
                    scale *= (float)(0.35 + 0.65 * (1.0 - progress));
                } else {
                    x = piece.position().x + piece.velocity().x * age;
                    y = piece.position().y + piece.velocity().y * age
                        + 0.5 * burst.gravity * age * age;
                    z = piece.position().z + piece.velocity().z * age;
                    alpha = (float)(1.0 - smoothstep(0.7, 1.0, progress));
                }
                Matrix4f model = new Matrix4f()
                    .translate((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                    .rotateY((float)Math.toRadians(piece.yaw() + piece.yawSpeed() * age))
                    .rotateX((float)Math.toRadians(piece.pitch() + piece.pitchSpeed() * age))
                    .rotateZ((float)Math.toRadians(piece.roll() + piece.rollSpeed() * age))
                    .scale(scale);
                instances.add(new InstancedBatchBuilder.Instance(
                    model,
                    burst.red,
                    burst.green,
                    burst.blue,
                    alpha,
                    burst.u0,
                    burst.v0,
                    burst.uScale,
                    burst.vScale,
                    (x - camera.x) * (x - camera.x) + (y - camera.y) * (y - camera.y)
                        + (z - camera.z) * (z - camera.z)
                ));
            }
        }
        if (instances.isEmpty()) {
            return;
        }

        InstancedBatchRenderer current = renderer();
        if (current == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Matrix4f baseView;
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            baseView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        } finally {
            poseStack.popPose();
        }
        try {
            current.draw(
                List.of(new InstancedBatchBuilder.InstancedDraw(
                    new InstancedBatchBuilder.BatchKey("debris", EffectRenderTypes.debris(), CUBE),
                    instances
                )),
                baseView,
                RenderSystem.getProjectionMatrix()
            );
        } catch (RuntimeException exception) {
            if (!renderFailureLogged) {
                renderFailureLogged = true;
                LOGGER.error("Could not render client effect debris", exception);
            }
        }
    }

    private static InstancedBatchRenderer renderer() {
        if (renderer == null) {
            meshCache = new GpuMeshCache();
            renderer = new InstancedBatchRenderer(meshCache);
        }
        return renderer;
    }

    private static int totalPieces() {
        int total = 0;
        for (Burst burst : BURSTS) {
            total += burst.pieces.size();
        }
        return total;
    }

    private static Vec3 shellPoint(RandomSource random, EffectDebrisSpawn.Shell shell) {
        double min = shell.minRadius();
        double max = shell.maxRadius();
        double radius = Math.cbrt(min * min * min
            + random.nextDouble() * (max * max * max - min * min * min));
        return shell.center().add(randomDirection(random).scale(radius));
    }

    private static Vec3 diskPoint(RandomSource random, EffectDebrisSpawn.Disk disk) {
        double min = disk.minRadius();
        double max = disk.maxRadius();
        double radius = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        double angle = random.nextDouble() * Math.PI * 2.0;
        return disk.center().add(
            Math.cos(angle) * radius,
            (random.nextDouble() * 2.0 - 1.0) * disk.ySpread(),
            Math.sin(angle) * radius
        );
    }

    private static Vec3 randomDirection(RandomSource random) {
        double x;
        double y;
        double z;
        double lengthSquared;
        do {
            x = random.nextDouble() * 2.0 - 1.0;
            y = random.nextDouble() * 2.0 - 1.0;
            z = random.nextDouble() * 2.0 - 1.0;
            lengthSquared = x * x + y * y + z * z;
        } while (lengthSquared <= 1.0E-6 || lengthSquared > 1.0);
        double scale = 1.0 / Math.sqrt(lengthSquared);
        return new Vec3(x * scale, y * scale, z * scale);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static EffectMesh createCube() {
        EffectMesh.Builder builder = EffectMesh.builder(24);
        addFace(builder, 1.0F, 0.0F, 0.0F,
            0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F);
        addFace(builder, -1.0F, 0.0F, 0.0F,
            -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F);
        addFace(builder, 0.0F, 1.0F, 0.0F,
            -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F);
        addFace(builder, 0.0F, -1.0F, 0.0F,
            -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F);
        addFace(builder, 0.0F, 0.0F, 1.0F,
            -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F);
        addFace(builder, 0.0F, 0.0F, -1.0F,
            0.5F, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F);
        return builder.build();
    }

    private static void addFace(
        EffectMesh.Builder builder,
        float normalX,
        float normalY,
        float normalZ,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3
    ) {
        builder.vertex(x0, y0, z0, 0.0F, 1.0F, normalX, normalY, normalZ);
        builder.vertex(x1, y1, z1, 1.0F, 1.0F, normalX, normalY, normalZ);
        builder.vertex(x2, y2, z2, 1.0F, 0.0F, normalX, normalY, normalZ);
        builder.vertex(x3, y3, z3, 0.0F, 0.0F, normalX, normalY, normalZ);
    }

    record Piece(
        Vec3 position,
        Vec3 velocity,
        float yaw,
        float pitch,
        float roll,
        float yawSpeed,
        float pitchSpeed,
        float rollSpeed,
        float scale,
        int lifetimeTicks
    ) {
        Piece {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(velocity, "velocity");
            if (lifetimeTicks < 1) {
                throw new IllegalArgumentException("Debris lifetime must be positive");
            }
        }
    }

    private static final class Burst {
        private final long startGameTime;
        private final int maxLifetimeTicks;
        private final double gravity;
        private final float u0;
        private final float v0;
        private final float uScale;
        private final float vScale;
        private final float red;
        private final float green;
        private final float blue;
        private final EffectDebrisAttractor attractor;
        private final float scale;
        private final List<Piece> pieces;

        private Burst(
            long startGameTime,
            int maxLifetimeTicks,
            double gravity,
            float u0,
            float v0,
            float uScale,
            float vScale,
            float red,
            float green,
            float blue,
            EffectDebrisAttractor attractor,
            float scale,
            List<Piece> pieces
        ) {
            this.startGameTime = startGameTime;
            this.maxLifetimeTicks = maxLifetimeTicks;
            this.gravity = gravity;
            this.u0 = u0;
            this.v0 = v0;
            this.uScale = uScale;
            this.vScale = vScale;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.attractor = attractor;
            this.scale = scale;
            this.pieces = List.copyOf(pieces);
        }

        private boolean isExpired(long serverTime) {
            return serverTime - this.startGameTime > this.maxLifetimeTicks;
        }
    }
}
