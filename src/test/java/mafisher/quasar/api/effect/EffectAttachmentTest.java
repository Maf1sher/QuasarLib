package mafisher.quasar.api.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EffectAttachmentTest {
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void noneKeepsTheLocalOffsetInWorldAxes() {
        EffectAttachment attachment = new EffectAttachment(
            new EffectAttachment.Parent.Entity(PARENT_ID),
            transform(1.0, 2.0, 3.0, 4.0F, 5.0F, 6.0F),
            EffectAttachment.RotationInheritance.NONE,
            EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT
        );

        EffectTransform resolved = attachment.resolve(transform(10.0, 64.0, -5.0, 30.0F, 20.0F, 10.0F));

        assertPoint(11.0, 66.0, -2.0, resolved);
        assertEquals(4.0F, resolved.yaw(), 0.0F);
        assertEquals(5.0F, resolved.pitch(), 0.0F);
        assertEquals(6.0F, resolved.roll(), 0.0F);
    }

    @Test
    void yawOnlyRotatesTheOffsetAndInheritsYaw() {
        EffectAttachment attachment = new EffectAttachment(
            new EffectAttachment.Parent.Entity(PARENT_ID),
            transform(1.0, 0.0, 0.0, 5.0F, 6.0F, 7.0F),
            EffectAttachment.RotationInheritance.YAW_ONLY,
            EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT
        );

        EffectTransform resolved = attachment.resolve(transform(10.0, 64.0, -5.0, 90.0F, 20.0F, 10.0F));
        Vector3f expected = EffectSpace.Basis.fromEuler(90.0F, 0.0F, 0.0F)
            .transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));

        assertPoint(10.0 + expected.x(), 64.0, -5.0 + expected.z(), resolved);
        assertEquals(95.0F, resolved.yaw(), 0.0F);
        assertEquals(6.0F, resolved.pitch(), 0.0F);
        assertEquals(7.0F, resolved.roll(), 0.0F);
    }

    @Test
    void fullRotatesTheOffsetWithTheParentBasisAndAddsAngles() {
        EffectAttachment attachment = new EffectAttachment(
            new EffectAttachment.Parent.Effect(PARENT_ID),
            transform(1.0, 0.0, 0.0, 5.0F, 6.0F, 7.0F),
            EffectAttachment.RotationInheritance.FULL,
            EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT
        );
        EffectTransform parent = transform(10.0, 64.0, -5.0, 90.0F, 0.0F, 0.0F);

        EffectTransform resolved = attachment.resolve(parent);
        Vector3f expected = EffectSpace.Basis.fromEuler(parent.yaw(), parent.pitch(), parent.roll())
            .transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));

        assertPoint(10.0 + expected.x(), 64.0 + expected.y(), -5.0 + expected.z(), resolved);
        assertEquals(95.0F, resolved.yaw(), 0.0F);
        assertEquals(6.0F, resolved.pitch(), 0.0F);
        assertEquals(7.0F, resolved.roll(), 0.0F);
    }

    @Test
    void factoriesChooseSafeDefaults() {
        EffectAttachment entity = EffectAttachment.toEntity(
            PARENT_ID, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        );
        EffectAttachment subLevel = EffectAttachment.toSubLevel(
            PARENT_ID, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        );

        assertEquals(EffectAttachment.RotationInheritance.FULL, entity.rotationInheritance());
        assertEquals(EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT, entity.parentLossPolicy());
        assertEquals(EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST, subLevel.parentLossPolicy());
        assertEquals(new EffectAttachment.Parent.SubLevel(PARENT_ID), subLevel.parent());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(NullPointerException.class, () -> new EffectAttachment(
            null, transform(0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F),
            EffectAttachment.RotationInheritance.NONE,
            EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT
        ));
        assertThrows(NullPointerException.class,
            () -> new EffectAttachment.Parent.Entity(null));
        assertThrows(NullPointerException.class, () -> EffectAttachment.toEntity(
            PARENT_ID, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        ).resolve(null));
    }

    private static EffectTransform transform(
        double x, double y, double z, float yaw, float pitch, float roll
    ) {
        return new EffectTransform(new EffectPoint(x, y, z), yaw, pitch, roll);
    }

    private static void assertPoint(double x, double y, double z, EffectTransform transform) {
        assertEquals(x, transform.position().x(), 1.0E-5);
        assertEquals(y, transform.position().y(), 1.0E-5);
        assertEquals(z, transform.position().z(), 1.0E-5);
    }
}
