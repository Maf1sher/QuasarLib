package mafisher.quasar.api.effect;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.budget.EffectLimits;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.effect.network.EffectPayloadLimits;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Server-to-client start event for one short-lived, client-rendered effect.
 *
 * @param startGameTime absolute server game time at which the effect started
 * @param serverGameTime server game time when this payload was created; clients use it to
 *     calibrate their own monotonic server timeline
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record StartVisualEffectPayload(
    ResourceLocation effectId,
    UUID instanceId,
    ResourceLocation dimensionId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float roll,
    float scale,
    int durationTicks,
    long startGameTime,
    long serverGameTime,
    long seed,
    @Nullable SegmentLink segmentLink,
    @Nullable EffectAttachment attachment
) implements CustomPacketPayload {
    public static final Type<StartVisualEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "start_visual_effect")
    );

    public static final StreamCodec<ByteBuf, StartVisualEffectPayload> STREAM_CODEC = StreamCodec.of(
        StartVisualEffectPayload::encode,
        StartVisualEffectPayload::decode
    );

    public StartVisualEffectPayload {
        EffectPayloadLimits.requireIdentifier(effectId, "Effect ID");
        Objects.requireNonNull(instanceId, "instanceId");
        EffectPayloadLimits.requireIdentifier(dimensionId, "Payload dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(scale) || scale <= 0.0F || scale > EffectLimits.MAX_SCALE
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid visual effect payload");
        }
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long startGameTime,
        long serverGameTime,
        long seed,
        SegmentLink segmentLink
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, roll, scale, durationTicks,
            startGameTime, serverGameTime, seed, segmentLink, null);
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed,
        SegmentLink segmentLink
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, roll, scale, durationTicks, startGameTime,
            startGameTime, seed, segmentLink, null);
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, roll, scale, durationTicks, startGameTime, seed,
            null);
    }

    public StartVisualEffectPayload(
        ResourceLocation effectId,
        UUID instanceId,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float scale,
        int durationTicks,
        long startGameTime,
        long seed
    ) {
        this(effectId, instanceId, dimensionId, x, y, z, yaw, pitch, 0.0F, scale, durationTicks, startGameTime, seed,
            null);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.x, this.y, this.z),
            this.yaw, this.pitch, this.roll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ByteBuf buffer, StartVisualEffectPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.effectId);
        UUIDUtil.STREAM_CODEC.encode(buffer, payload.instanceId);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.yaw);
        buffer.writeFloat(payload.pitch);
        buffer.writeFloat(payload.roll);
        buffer.writeFloat(payload.scale);
        buffer.writeInt(payload.durationTicks);
        buffer.writeLong(payload.startGameTime);
        buffer.writeLong(payload.serverGameTime);
        buffer.writeLong(payload.seed);
        SegmentLink link = payload.segmentLink;
        buffer.writeBoolean(link != null);
        if (link != null) {
            UUIDUtil.STREAM_CODEC.encode(buffer, link.coordinatorId());
            buffer.writeByte(link.segmentIndex());
            buffer.writeByte(link.layout().columns());
            buffer.writeByte(link.layout().rows());
            buffer.writeByte(link.layout().layers());
            buffer.writeDouble(link.layout().spacing());
            buffer.writeDouble(link.layout().layerSpacing());
        }
        encodeAttachment(buffer, payload.attachment);
    }

    public static StartVisualEffectPayload decode(ByteBuf buffer) {
        return new StartVisualEffectPayload(
            ResourceLocation.STREAM_CODEC.decode(buffer),
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ResourceLocation.STREAM_CODEC.decode(buffer),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            decodeSegmentLink(buffer),
            decodeAttachment(buffer)
        );
    }

    private static void encodeAttachment(ByteBuf buffer, @Nullable EffectAttachment attachment) {
        buffer.writeBoolean(attachment != null);
        if (attachment == null) {
            return;
        }
        EffectAttachment.Parent parent = attachment.parent();
        if (parent instanceof EffectAttachment.Parent.Entity entity) {
            buffer.writeByte(0);
            UUIDUtil.STREAM_CODEC.encode(buffer, entity.id());
        } else if (parent instanceof EffectAttachment.Parent.Effect effect) {
            buffer.writeByte(1);
            UUIDUtil.STREAM_CODEC.encode(buffer, effect.id());
        } else {
            buffer.writeByte(2);
            UUIDUtil.STREAM_CODEC.encode(buffer, ((EffectAttachment.Parent.SubLevel) parent).id());
        }
        EffectTransform local = attachment.localTransform();
        buffer.writeDouble(local.position().x());
        buffer.writeDouble(local.position().y());
        buffer.writeDouble(local.position().z());
        buffer.writeFloat(local.yaw());
        buffer.writeFloat(local.pitch());
        buffer.writeFloat(local.roll());
        buffer.writeByte(attachment.rotationInheritance().ordinal());
        buffer.writeByte(attachment.parentLossPolicy().ordinal());
    }

    @Nullable
    private static EffectAttachment decodeAttachment(ByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        int kind = buffer.readUnsignedByte();
        UUID parentId = UUIDUtil.STREAM_CODEC.decode(buffer);
        EffectAttachment.Parent parent = switch (kind) {
            case 0 -> new EffectAttachment.Parent.Entity(parentId);
            case 1 -> new EffectAttachment.Parent.Effect(parentId);
            case 2 -> new EffectAttachment.Parent.SubLevel(parentId);
            default -> throw new IllegalArgumentException("Unknown effect attachment parent kind");
        };
        EffectTransform local = new EffectTransform(
            new EffectPoint(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat()
        );
        int rotation = buffer.readUnsignedByte();
        int lossPolicy = buffer.readUnsignedByte();
        EffectAttachment.RotationInheritance[] rotations =
            EffectAttachment.RotationInheritance.values();
        EffectAttachment.ParentLossPolicy[] policies =
            EffectAttachment.ParentLossPolicy.values();
        if (rotation >= rotations.length || lossPolicy >= policies.length) {
            throw new IllegalArgumentException("Unknown effect attachment enum value");
        }
        return new EffectAttachment(parent, local, rotations[rotation], policies[lossPolicy]);
    }

    private static SegmentLink decodeSegmentLink(ByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        UUID coordinatorId = UUIDUtil.STREAM_CODEC.decode(buffer);
        int segmentIndex = buffer.readUnsignedByte();
        int columns = buffer.readUnsignedByte();
        int rows = buffer.readUnsignedByte();
        int layers = buffer.readUnsignedByte();
        double spacing = buffer.readDouble();
        double layerSpacing = buffer.readDouble();
        return new SegmentLink(coordinatorId, segmentIndex,
            new SegmentGridLayout(columns, rows, layers, spacing, layerSpacing));
    }
}
