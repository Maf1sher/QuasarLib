package mafisher.quasar.effect.network;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.damage.EffectHitboxOutcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Operator-requested snapshot of server-authoritative gameplay hitboxes. It is sent only to the
 * player who ran {@code /quasarlib hitboxes}; clients use it for the inspector's hitbox gizmos and
 * never send it back.
 */
@ApiStatus.Internal
public record InspectHitboxPayload(
    long serverGameTime,
    ResourceLocation dimensionId,
    List<Session> sessions
) implements CustomPacketPayload {
    public static final int MAX_SESSIONS = 32;
    public static final int MAX_SHAPES_PER_SESSION = 4;
    public static final int MAX_SHAPES = 128;
    public static final int MAX_BYTES = 32 * 1_024;

    public static final Type<InspectHitboxPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "inspect_hitboxes")
    );
    public static final StreamCodec<FriendlyByteBuf, InspectHitboxPayload> STREAM_CODEC =
        StreamCodec.of(InspectHitboxPayload::encode, InspectHitboxPayload::decode);

    public InspectHitboxPayload {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(sessions, "sessions");
        EffectPayloadLimits.requireIdentifier(dimensionId, "dimensionId");
        if (sessions.size() > MAX_SESSIONS) {
            throw new IllegalArgumentException(
                "Hitbox snapshot supports at most " + MAX_SESSIONS + " sessions"
            );
        }
        int shapes = 0;
        List<Session> copied = new ArrayList<>(sessions.size());
        for (Session session : sessions) {
            Objects.requireNonNull(session, "session");
            shapes += session.shapes().size();
            copied.add(session);
        }
        if (shapes > MAX_SHAPES) {
            throw new IllegalArgumentException(
                "Hitbox snapshot supports at most " + MAX_SHAPES + " shapes"
            );
        }
        sessions = List.copyOf(copied);
    }

    @Override
    public Type<InspectHitboxPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, InspectHitboxPayload payload) {
        buffer.writeLong(payload.serverGameTime());
        buffer.writeResourceLocation(payload.dimensionId());
        buffer.writeVarInt(payload.sessions().size());
        for (Session session : payload.sessions()) {
            encodeSession(buffer, session);
        }
    }

    private static InspectHitboxPayload decode(FriendlyByteBuf buffer) {
        long serverGameTime = buffer.readLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        int sessionCount = buffer.readVarInt();
        if (sessionCount < 0 || sessionCount > MAX_SESSIONS) {
            throw new DecoderException("Invalid inspect hitbox session count: " + sessionCount);
        }
        List<Session> sessions = new ArrayList<>(sessionCount);
        int shapes = 0;
        for (int index = 0; index < sessionCount; index++) {
            Session session = decodeSession(buffer);
            shapes += session.shapes().size();
            if (shapes > MAX_SHAPES) {
                throw new DecoderException("Inspect hitbox snapshot exceeds the shape cap");
            }
            sessions.add(session);
        }
        try {
            return new InspectHitboxPayload(serverGameTime, dimensionId, sessions);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException(exception.getMessage(), exception);
        }
    }

    private static void encodeSession(FriendlyByteBuf buffer, Session session) {
        buffer.writeUUID(session.sessionId());
        buffer.writeResourceLocation(session.typeId());
        buffer.writeVarInt(session.ageTicks());
        buffer.writeVarInt(session.phaseIndex());
        buffer.writeVarInt(session.phaseCount());
        buffer.writeVarInt(session.shapes().size());
        for (Shape shape : session.shapes()) {
            encodeShape(buffer, shape.shape());
            buffer.writeByte(outcomeId(shape.outcome()));
        }
    }

    private static int outcomeId(EffectHitboxOutcome outcome) {
        return switch (outcome) {
            case DAMAGE -> 0;
            case REMOVE -> 1;
        };
    }

    private static Session decodeSession(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceLocation typeId = buffer.readResourceLocation();
        int ageTicks = buffer.readVarInt();
        int phaseIndex = buffer.readVarInt();
        int phaseCount = buffer.readVarInt();
        int shapeCount = buffer.readVarInt();
        if (shapeCount < 0 || shapeCount > MAX_SHAPES_PER_SESSION) {
            throw new DecoderException("Invalid inspect hitbox shape count: " + shapeCount);
        }
        List<Shape> shapes = new ArrayList<>(shapeCount);
        for (int index = 0; index < shapeCount; index++) {
            EffectDamageShape shape = decodeShape(buffer);
            EffectHitboxOutcome outcome = switch (buffer.readUnsignedByte()) {
                case 0 -> EffectHitboxOutcome.DAMAGE;
                case 1 -> EffectHitboxOutcome.REMOVE;
                default -> throw new DecoderException("Invalid inspect hitbox outcome");
            };
            shapes.add(new Shape(shape, outcome));
        }
        try {
            return new Session(sessionId, typeId, ageTicks, phaseIndex, phaseCount, shapes);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException(exception.getMessage(), exception);
        }
    }

    private static void encodeShape(FriendlyByteBuf buffer, EffectDamageShape shape) {
        if (shape instanceof EffectDamageShape.Box box) {
            buffer.writeByte(0);
            writeBounds(buffer, box.bounds());
        } else if (shape instanceof EffectDamageShape.Sphere sphere) {
            buffer.writeByte(1);
            writePoint(buffer, sphere.center());
            buffer.writeDouble(sphere.radius());
        } else if (shape instanceof EffectDamageShape.Cylinder cylinder) {
            buffer.writeByte(2);
            writePoint(buffer, cylinder.base());
            buffer.writeDouble(cylinder.radius());
            buffer.writeDouble(cylinder.height());
        } else if (shape instanceof EffectDamageShape.Beam beam) {
            buffer.writeByte(3);
            writePoint(buffer, beam.start());
            writePoint(buffer, beam.end());
            buffer.writeDouble(beam.radius());
        } else {
            throw new IllegalArgumentException("Unknown effect damage shape: " + shape);
        }
    }

    private static EffectDamageShape decodeShape(FriendlyByteBuf buffer) {
        int type = buffer.readUnsignedByte();
        try {
            return switch (type) {
                case 0 -> new EffectDamageShape.Box(readBounds(buffer));
                case 1 -> new EffectDamageShape.Sphere(readPoint(buffer), buffer.readDouble());
                case 2 -> new EffectDamageShape.Cylinder(
                    readPoint(buffer), buffer.readDouble(), buffer.readDouble()
                );
                case 3 -> new EffectDamageShape.Beam(
                    readPoint(buffer), readPoint(buffer), buffer.readDouble()
                );
                default -> throw new DecoderException("Unknown effect damage shape type: " + type);
            };
        } catch (IllegalArgumentException exception) {
            throw new DecoderException(exception.getMessage(), exception);
        }
    }

    private static void writeBounds(FriendlyByteBuf buffer, EffectBounds bounds) {
        buffer.writeDouble(bounds.minX());
        buffer.writeDouble(bounds.minY());
        buffer.writeDouble(bounds.minZ());
        buffer.writeDouble(bounds.maxX());
        buffer.writeDouble(bounds.maxY());
        buffer.writeDouble(bounds.maxZ());
    }

    private static EffectBounds readBounds(FriendlyByteBuf buffer) {
        return new EffectBounds(
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
        );
    }

    private static void writePoint(FriendlyByteBuf buffer, EffectPoint point) {
        buffer.writeDouble(point.x());
        buffer.writeDouble(point.y());
        buffer.writeDouble(point.z());
    }

    private static EffectPoint readPoint(FriendlyByteBuf buffer) {
        return new EffectPoint(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    /** One active gameplay session with its currently tracked hitboxes. */
    public record Session(
        UUID sessionId,
        ResourceLocation typeId,
        int ageTicks,
        int phaseIndex,
        int phaseCount,
        List<Shape> shapes
    ) {
        public Session {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(shapes, "shapes");
            EffectPayloadLimits.requireIdentifier(typeId, "typeId");
            if (ageTicks < 0 || phaseIndex < 0 || phaseCount < 1 || shapes.isEmpty()
                || shapes.size() > MAX_SHAPES_PER_SESSION) {
                throw new IllegalArgumentException("Invalid inspect hitbox session");
            }
            List<Shape> copied = new ArrayList<>(shapes.size());
            for (Shape shape : shapes) {
                copied.add(Objects.requireNonNull(shape, "shape"));
            }
            shapes = List.copyOf(copied);
        }
    }

    /** One hitbox shape and its outcome. */
    public record Shape(EffectDamageShape shape, EffectHitboxOutcome outcome) {
        public Shape {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(outcome, "outcome");
        }
    }
}
