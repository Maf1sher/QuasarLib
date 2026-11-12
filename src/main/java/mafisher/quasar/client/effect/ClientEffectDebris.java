package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.EffectMesh;
import mafisher.quasar.api.effect.EffectDebrisAttractor;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import mafisher.quasar.effect.EffectDebrisMath;
import mafisher.quasar.effect.network.EffectDebrisPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Lightweight client-only block debris. Bursts are simulated from a seed and never collide with
 * terrain; visible pieces are emitted as textured block cubes through the vanilla entity batch,
 * which keeps the effect compatible with Sodium and Iris.
 */
final class ClientEffectDebris {
    static final int MAX_ACTIVE_PIECES = 6_144;

    private static final int FULLBRIGHT_LIGHT = 0x00F000F0;
    private static final EffectMesh CUBE = createCube();
    private static final PoseStack POSE_STACK = new PoseStack();
    private static final RenderType DEBRIS_RENDER_TYPE =
        RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    private static final List<Burst> BURSTS = new ArrayList<>();

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
        List<Piece> burstPieces = pieces(payload, surfaceResolver(payload, level, minecraft));
        if (burstPieces.isEmpty()) {
            return;
        }
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
            burstPieces
        );
        BURSTS.add(burst);
        while (totalPieces() > MAX_ACTIVE_PIECES && BURSTS.size() > 1) {
            BURSTS.removeFirst();
        }
    }

    static void clear() {
        BURSTS.clear();
    }

    static void onResourceReload() {
        clear();
    }

    static int activePieces() {
        return totalPieces();
    }

    /** Deterministic piece state for one payload; exposed to headless tests. */
    static List<Piece> pieces(EffectDebrisPayload payload) {
        return pieces(payload, null);
    }

    /** Deterministic piece state; a resolver projects surface spawns onto real terrain. */
    static List<Piece> pieces(EffectDebrisPayload payload, SurfaceResolver resolver) {
        Objects.requireNonNull(payload, "payload");
        RandomSource random = RandomSource.create(payload.seed());
        boolean surfaceSpawn = payload.spawn() instanceof EffectDebrisSpawn.Surface;
        List<Piece> pieces = new ArrayList<>(payload.pieces());
        List<Vec3> unresolvedSurface = new ArrayList<>();
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
            Appearance appearance = null;
            if (payload.spawn() instanceof EffectDebrisSpawn.Shell shell) {
                origin = shellPoint(random, shell);
            } else if (payload.spawn() instanceof EffectDebrisSpawn.Disk disk) {
                origin = diskPoint(random, disk);
            } else if (payload.spawn() instanceof EffectDebrisSpawn.Surface surface) {
                Vec3 candidate = surfacePoint(random, surface);
                if (resolver == null) {
                    origin = candidate;
                } else {
                    Sample sample = resolver.resolve(candidate, surface.lift());
                    if (sample == null) {
                        unresolvedSurface.add(candidate);
                        continue;
                    }
                    origin = sample.position();
                    appearance = sample.appearance();
                }
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
            float baseScale = surfaceSpawn
                ? 0.12F + random.nextFloat() * 0.10F
                : 0.075F + random.nextFloat() * 0.075F;
            pieces.add(new Piece(
                origin,
                direction.scale(speed),
                random.nextFloat() * 360.0F,
                random.nextFloat() * 360.0F,
                random.nextFloat() * 360.0F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                baseScale,
                lifetime,
                appearance
            ));
        }
        if (surfaceSpawn && resolver != null && pieces.isEmpty() && !unresolvedSurface.isEmpty()) {
            // Terrain sampling failed for every column (for example an unloaded horizon area).
            // Fall back to the candidate columns so the effect still reads as blocks leaving the
            // ground instead of silently dropping the whole burst.
            for (Vec3 origin : unresolvedSurface) {
                int lifetime = Math.max(1, (int)Math.round(
                    payload.lifetimeTicks() * (0.7 + random.nextDouble() * 0.3)
                ));
                pieces.add(new Piece(
                    origin,
                    Vec3.ZERO,
                    random.nextFloat() * 360.0F,
                    random.nextFloat() * 360.0F,
                    random.nextFloat() * 360.0F,
                    (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                    (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                    (random.nextFloat() * 2.0F - 1.0F) * 0.25F,
                    0.12F + random.nextFloat() * 0.1F,
                    lifetime,
                    null
                ));
            }
        }
        return List.copyOf(pieces);
    }

    private static SurfaceResolver surfaceResolver(
        EffectDebrisPayload payload,
        ClientLevel level,
        Minecraft minecraft
    ) {
        if (!(payload.spawn() instanceof EffectDebrisSpawn.Surface)) {
            return null;
        }
        return (candidate, lift) -> sampleSurface(level, minecraft, candidate, lift);
    }

    /**
     * Projects a candidate column onto the client terrain, samples the block that is actually there,
     * and derives its sprite and tint. Returns {@code null} when the column is unloaded or empty so
     * the piece is skipped instead of floating in mid-air.
     */
    private static Sample sampleSurface(
        ClientLevel level,
        Minecraft minecraft,
        Vec3 candidate,
        double lift
    ) {
        int x = Mth.floor(candidate.x);
        int z = Mth.floor(candidate.z);
        if (!level.getChunkSource().hasChunk(
            SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)
        )) {
            return null;
        }
        BlockPos position = new BlockPos(
            x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z
        );
        if (!level.isInWorldBounds(position)) {
            return null;
        }
        BlockState state = level.getBlockState(position);
        for (int step = 0; step < 3 && (state.isAir() || !state.getFluidState().isEmpty()); step++) {
            position = position.below();
            if (!level.isInWorldBounds(position)) {
                return null;
            }
            state = level.getBlockState(position);
        }
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return null;
        }
        TextureAtlasSprite sprite = minecraft.getBlockRenderer().getBlockModelShaper()
            .getParticleIcon(state);
        if (sprite == null) {
            return null;
        }
        int tint = minecraft.getBlockColors().getColor(state, level, position, 0);
        float red = tint < 0 ? 1.0F : ((tint >> 16) & 0xFF) / 255.0F;
        float green = tint < 0 ? 1.0F : ((tint >> 8) & 0xFF) / 255.0F;
        float blue = tint < 0 ? 1.0F : (tint & 0xFF) / 255.0F;
        Vec3 start = new Vec3(x + 0.5, position.getY() + 1.0 + lift, z + 0.5);
        return new Sample(start, new Appearance(
            sprite.getU0(),
            sprite.getV0(),
            sprite.getU1() - sprite.getU0(),
            sprite.getV1() - sprite.getV0(),
            red,
            green,
            blue
        ));
    }

    static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        if (BURSTS.isEmpty()) {
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
        Vector3f look = event.getCamera().getLookVector();
        PoseStack poseStack = POSE_STACK;
        while (!poseStack.clear()) {
            poseStack.popPose();
        }
        poseStack.setIdentity();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(DEBRIS_RENDER_TYPE);
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
                    scale *= (float)infallScale(progress);
                } else {
                    x = piece.position().x + piece.velocity().x * age;
                    y = piece.position().y + piece.velocity().y * age
                        + 0.5 * burst.gravity * age * age;
                    z = piece.position().z + piece.velocity().z * age;
                }
                double dx = x - camera.x;
                double dy = y - camera.y;
                double dz = z - camera.z;
                if (dx * look.x() + dy * look.y() + dz * look.z() < -8.0
                    || dx * dx + dy * dy + dz * dz > 512.0 * 512.0) {
                    continue;
                }
                Appearance appearance = piece.appearance();
                poseStack.pushPose();
                try {
                    poseStack.translate(x, y, z);
                    poseStack.mulPose(Axis.YP.rotationDegrees(
                        piece.yaw() + piece.yawSpeed() * (float)age
                    ));
                    poseStack.mulPose(Axis.XP.rotationDegrees(
                        piece.pitch() + piece.pitchSpeed() * (float)age
                    ));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(
                        piece.roll() + piece.rollSpeed() * (float)age
                    ));
                    poseStack.scale(scale, scale, scale);
                    emitCube(
                        consumer,
                        poseStack.last(),
                        appearance == null ? burst.u0 : appearance.u0(),
                        appearance == null ? burst.v0 : appearance.v0(),
                        appearance == null ? burst.uScale : appearance.uScale(),
                        appearance == null ? burst.vScale : appearance.vScale(),
                        appearance == null ? burst.red : appearance.red(),
                        appearance == null ? burst.green : appearance.green(),
                        appearance == null ? burst.blue : appearance.blue()
                    );
                } finally {
                    poseStack.popPose();
                }
            }
        }
        buffers.endBatch(DEBRIS_RENDER_TYPE);
    }

    /**
     * Keeps a piece block-sized for most of the fall, then shrinks it in the last stretch so it
     * reads as a whole block entering the core instead of a shard that erodes over the whole trip.
     */
    private static double infallScale(double progress) {
        double tail = 1.0 - Math.max(0.0, Math.min(1.0, progress));
        if (tail >= 0.20) {
            return 1.0;
        }
        return 0.15 + 0.85 * (tail / 0.20);
    }

    private static void emitCube(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float u0,
        float v0,
        float uScale,
        float vScale,
        float red,
        float green,
        float blue
    ) {
        for (int index = 0; index < CUBE.vertexCount(); index++) {
            EffectMesh.Vertex vertex = CUBE.vertex(index);
            consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(red, green, blue, 1.0F)
                .setUv(u0 + vertex.u() * uScale, v0 + vertex.v() * vScale)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT_LIGHT)
                .setNormal(pose, vertex.normalX(), vertex.normalY(), vertex.normalZ());
        }
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

    /** Candidate column inside the annulus; the resolver replaces the vertical component. */
    private static Vec3 surfacePoint(RandomSource random, EffectDebrisSpawn.Surface surface) {
        double min = surface.minRadius();
        double max = surface.maxRadius();
        double radius = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        double angle = random.nextDouble() * Math.PI * 2.0;
        return surface.center().add(
            Math.cos(angle) * radius,
            0.0,
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

    /** Client-side appearance sampled from the terrain for one surface piece. */
    record Appearance(
        float u0,
        float v0,
        float uScale,
        float vScale,
        float red,
        float green,
        float blue
    ) {
    }

    /** Resolved surface start point plus its sprite and tint. */
    record Sample(Vec3 position, Appearance appearance) {
        Sample {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(appearance, "appearance");
        }
    }

    /** Projects a candidate column onto local terrain; returns {@code null} to skip the piece. */
    @FunctionalInterface
    interface SurfaceResolver {
        Sample resolve(Vec3 candidate, double lift);
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
        int lifetimeTicks,
        Appearance appearance
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
