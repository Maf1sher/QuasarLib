package mafisher.quasar.api.effect;

import java.util.Objects;
import java.util.UUID;
import org.joml.Vector3f;

/**
 * Immutable description of an effect placed relative to one parent object.
 *
 * <p>The local transform is authored in the parent's frame: its position is a local offset and
 * its Euler angles are a relative rotation. The engine resolves the parent at render time, so a
 * moving parent never needs per-tick transform updates. Rotation is composed in the engine's
 * documented {@code Y * X * Z} Euler convention; exact six-degree-of-freedom attachment is not
 * part of this contract.</p>
 *
 * <p>An attachment is presentation data. The server remains authoritative for lifetime,
 * audience, and persistence; the client only resolves where the effect is drawn.</p>
 */
public record EffectAttachment(
    Parent parent,
    EffectTransform localTransform,
    RotationInheritance rotationInheritance,
    ParentLossPolicy parentLossPolicy
) {
    /** Maximum supported attachment chain length in effects, including the child. */
    public static final int MAX_CHAIN_DEPTH = 16;

    public EffectAttachment {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(localTransform, "localTransform");
        Objects.requireNonNull(rotationInheritance, "rotationInheritance");
        Objects.requireNonNull(parentLossPolicy, "parentLossPolicy");
    }

    /** Attaches to a vanilla entity in the same level. */
    public static EffectAttachment toEntity(
        UUID entityId,
        EffectPoint offset,
        float yaw,
        float pitch,
        float roll
    ) {
        return new EffectAttachment(
            new Parent.Entity(entityId),
            new EffectTransform(offset, yaw, pitch, roll),
            RotationInheritance.FULL,
            ParentLossPolicy.STOP_WITH_PARENT
        );
    }

    /** Attaches to another standalone visual effect in the same level. */
    public static EffectAttachment toEffect(
        UUID instanceId,
        EffectPoint offset,
        float yaw,
        float pitch,
        float roll
    ) {
        return new EffectAttachment(
            new Parent.Effect(instanceId),
            new EffectTransform(offset, yaw, pitch, roll),
            RotationInheritance.FULL,
            ParentLossPolicy.STOP_WITH_PARENT
        );
    }

    /**
     * Attaches to a Sable-style sub-level. Optional providers may be absent; a missing provider
     * falls back to the spec's authored world transform instead of stopping the effect. The local
     * offset only takes effect once a provider supplies the sub-level pose.
     */
    public static EffectAttachment toSubLevel(
        UUID subLevelId,
        EffectPoint offset,
        float yaw,
        float pitch,
        float roll
    ) {
        return new EffectAttachment(
            new Parent.SubLevel(subLevelId),
            new EffectTransform(offset, yaw, pitch, roll),
            RotationInheritance.FULL,
            ParentLossPolicy.FREEZE_AT_LAST
        );
    }

    public EffectAttachment withRotationInheritance(RotationInheritance inheritance) {
        return new EffectAttachment(
            this.parent, this.localTransform, inheritance, this.parentLossPolicy
        );
    }

    public EffectAttachment withParentLossPolicy(ParentLossPolicy policy) {
        return new EffectAttachment(
            this.parent, this.localTransform, this.rotationInheritance, policy
        );
    }

    /**
     * Resolves this attachment against the parent's current world transform.
     *
     * <p>{@link RotationInheritance#FULL} rotates the local offset with the full parent basis and
     * adds the parent angles in the engine Euler order. {@link RotationInheritance#YAW_ONLY}
     * rotates the offset around the parent's Y axis and inherits only yaw. {@code NONE} keeps the
     * local offset and local angles in world axes.</p>
     */
    public EffectTransform resolve(EffectTransform parentFrame) {
        Objects.requireNonNull(parentFrame, "parentFrame");
        EffectPoint local = this.localTransform.position();
        double offsetX = local.x();
        double offsetY = local.y();
        double offsetZ = local.z();
        float yaw = this.localTransform.yaw();
        float pitch = this.localTransform.pitch();
        float roll = this.localTransform.roll();
        if (this.rotationInheritance == RotationInheritance.YAW_ONLY) {
            Vector3f rotated = EffectSpace.Basis.fromEuler(parentFrame.yaw(), 0.0F, 0.0F)
                .transformDirection(new Vector3f((float) offsetX, (float) offsetY, (float) offsetZ));
            offsetX = rotated.x();
            offsetY = rotated.y();
            offsetZ = rotated.z();
            yaw += parentFrame.yaw();
        } else if (this.rotationInheritance == RotationInheritance.FULL) {
            Vector3f rotated = EffectSpace.Basis.fromEuler(
                parentFrame.yaw(), parentFrame.pitch(), parentFrame.roll()
            ).transformDirection(new Vector3f((float) offsetX, (float) offsetY, (float) offsetZ));
            offsetX = rotated.x();
            offsetY = rotated.y();
            offsetZ = rotated.z();
            yaw += parentFrame.yaw();
            pitch += parentFrame.pitch();
            roll += parentFrame.roll();
        }
        return new EffectTransform(
            new EffectPoint(
                parentFrame.position().x() + offsetX,
                parentFrame.position().y() + offsetY,
                parentFrame.position().z() + offsetZ
            ),
            yaw,
            pitch,
            roll
        );
    }

    /** Parent kinds that can carry an attached visual. */
    public sealed interface Parent permits Parent.Entity, Parent.Effect, Parent.SubLevel {
        /** Stable identity of the parent in its own domain. */
        UUID id();

        /** A vanilla entity. */
        record Entity(UUID id) implements Parent {
            public Entity {
                Objects.requireNonNull(id, "id");
            }
        }

        /** Another standalone visual effect. */
        record Effect(UUID id) implements Parent {
            public Effect {
                Objects.requireNonNull(id, "id");
            }
        }

        /** A sub-level supplied by an optional physics bridge. */
        record SubLevel(UUID id) implements Parent {
            public SubLevel {
                Objects.requireNonNull(id, "id");
            }
        }
    }

    /** How much of the parent's orientation the child inherits. */
    public enum RotationInheritance {
        NONE,
        YAW_ONLY,
        FULL
    }

    /** What happens when the parent disappears or cannot be resolved. */
    public enum ParentLossPolicy {
        /** Stop the child with the parent. */
        STOP_WITH_PARENT,
        /** Keep the last resolved world transform, or the authored transform if none exists. */
        FREEZE_AT_LAST
    }
}
