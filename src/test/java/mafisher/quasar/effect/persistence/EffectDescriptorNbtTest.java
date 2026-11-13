package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
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
    void roundTripsAnAttachmentAndKeepsOldSavesStandalone() {
        EffectDescriptor descriptor = EffectDescriptor.builder(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                EffectKind.VISUAL,
                TYPE,
                DIMENSION
            )
            .placement(new EffectTransform(new EffectPoint(1.0, 64.0, -2.0), 10.0F, 20.0F, 30.0F), 2.5F)
            .timing(1234L, 42L, 200)
            .audience(EffectAudience.dimensionWide())
            .attachment(EffectAttachment.toEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new EffectPoint(0.5, 1.0, -0.5),
                1.0F,
                2.0F,
                3.0F
            ))
            .state(new CompoundTag())
            .build();

        EffectDescriptor decoded = EffectDescriptorNbt.load(EffectDescriptorNbt.save(descriptor));

        assertEquals(descriptor, decoded);
        assertEquals(descriptor.attachment(), decoded.attachment());

        CompoundTag oldTag = EffectDescriptorNbt.save(descriptor(new CompoundTag()));
        oldTag.remove("attachment");
        assertEquals(null, EffectDescriptorNbt.load(oldTag).attachment());
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
    void transformUpdatesReuseOwnedState() {
        EffectDescriptor descriptor = descriptor(new CompoundTag());
        EffectDescriptor updated = descriptor.withTransform(
            new EffectTransform(new EffectPoint(3.0, 70.0, 4.0), 1.0F, 2.0F, 3.0F)
        );

        assertNotSame(descriptor, updated);
        assertSame(descriptor.stateView(), updated.stateView());
        assertEquals(descriptor.state(), updated.state());
    }

    @Test
    void withStateCopiesOnlyTheNewState() {
        EffectDescriptor descriptor = descriptor(new CompoundTag());
        CompoundTag state = new CompoundTag();
        state.putString("phase", "strike");

        EffectDescriptor updated = descriptor.withState(state);
        state.putString("phase", "mutated");

        assertEquals("strike", updated.state().getString("phase"));
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
        descriptor = EffectDescriptor.builder(descriptor.instanceId(), descriptor.kind(), descriptor.typeId(), descriptor.dimensionId())
            .placement(descriptor.transform(), descriptor.scale())
            .timing(descriptor.seed(), descriptor.startGameTime(), descriptor.durationTicks())
            .audience(descriptor.audience())
            .capabilities(new EffectCapabilities(true, Set.of(TYPE)))
            .persistence(EffectPersistence.COSMETIC)
            .ownerSessionId(descriptor.ownerSessionId())
            .state(descriptor.state())
            .build();

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
        return EffectDescriptor.builder(UUID.fromString("00000000-0000-0000-0000-000000000001"), EffectKind.VISUAL, TYPE, DIMENSION)
            .placement(new EffectTransform(new EffectPoint(1.0, 64.0, -2.0), 10.0F, 20.0F, 30.0F), 2.5F)
            .timing(1234L, 42L, 200)
            .audience(EffectAudience.nearby(64.0))
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(state)
            .build();
    }
}
