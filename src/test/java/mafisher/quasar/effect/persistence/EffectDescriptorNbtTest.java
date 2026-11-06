package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectDescriptorNbtTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsEveryField() {
        CompoundTag state = new CompoundTag();
        state.putString("phase", "strike");
        EffectDescriptor descriptor = descriptor(state);

        EffectDescriptor decoded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(descriptor, decoded);
        assertEquals(42L, decoded.startGameTime());
        assertEquals(EffectKind.VISUAL, decoded.kind());
        assertEquals(EffectPersistence.RESUMABLE, decoded.persistence());
        assertEquals("strike", decoded.state().getString("phase"));
    }

    @Test
    void keepsStateDefensivelyCopied() {
        CompoundTag state = new CompoundTag();
        state.putString("phase", "telegraph");
        EffectDescriptor descriptor = descriptor(state);

        state.putString("phase", "changed");
        CompoundTag read = descriptor.state();
        read.putString("phase", "mutated");

        assertEquals("telegraph", descriptor.state().getString("phase"));
    }

    @Test
    void rejectsUnknownTypeIds() {
        CompoundTag tag = EffectDescriptorNbt.save(descriptor(new CompoundTag()));
        tag.putString("type_id", "not a resource location");

        assertThrows(IllegalArgumentException.class, () -> EffectDescriptorNbt.load(tag));
    }

    @Test
    void roundTripsCapabilitiesAndOwnership() {
        EffectDescriptor descriptor = descriptor(new CompoundTag()).withOwnerSessionId(UUID.randomUUID());
        descriptor = new EffectDescriptor(
            descriptor.instanceId(),
            descriptor.kind(),
            descriptor.typeId(),
            descriptor.dimensionId(),
            descriptor.transform(),
            descriptor.scale(),
            descriptor.seed(),
            descriptor.startGameTime(),
            descriptor.durationTicks(),
            descriptor.audience(),
            new EffectCapabilities(true, Set.of(TYPE)),
            EffectPersistence.COSMETIC,
            descriptor.ownerSessionId(),
            descriptor.state()
        );

        EffectDescriptor decoded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertTrue(decoded.capabilities().destructive());
        assertTrue(decoded.capabilities().supports(TYPE));
        assertEquals(EffectPersistence.COSMETIC, decoded.persistence());
        assertEquals(descriptor.ownerSessionId(), decoded.ownerSessionId());
        CompoundTag otherState = new CompoundTag();
        otherState.putString("different", "state");
        assertNotEquals(descriptor, descriptor.withState(otherState));
    }

    @Test
    void reportsExpiryFromAbsoluteTime() {
        EffectDescriptor descriptor = descriptor(new CompoundTag());

        assertFalse(descriptor.isExpired(41L));
        assertTrue(descriptor.isExpired(42L + descriptor.durationTicks()));
    }

    private static EffectDescriptor descriptor(CompoundTag state) {
        return new EffectDescriptor(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            EffectKind.VISUAL,
            TYPE,
            DIMENSION,
            new EffectTransform(new EffectPoint(1.0, 64.0, -2.0), 10.0F, 20.0F, 30.0F),
            2.5F,
            1234L,
            42L,
            200,
            EffectAudience.nearby(64.0),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            state
        );
    }
}
