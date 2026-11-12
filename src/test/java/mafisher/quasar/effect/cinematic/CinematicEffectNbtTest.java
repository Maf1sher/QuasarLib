package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CinematicEffectNbtTest {
    private static final ResourceLocation TIMELINE_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "cinematic_nbt");
    private static final UUID SESSION_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000039");

    @Test
    void roundTripsAPersistedAnchor() {
        CompoundTag state = new CompoundTag();
        Vec3 anchor = new Vec3(12.0, 70.0, -4.0);

        CinematicEffectNbt.saveAnchor(state, anchor);

        EffectDescriptor descriptor = descriptor(anchor, state);
        assertEquals(anchor, CinematicEffectNbt.anchorOrNull(descriptor));
    }

    @Test
    void zeroAnchorStaysUnanchored() {
        CompoundTag state = new CompoundTag();

        CinematicEffectNbt.saveAnchor(state, Vec3.ZERO);

        assertNull(CinematicEffectNbt.anchorOrNull(descriptor(Vec3.ZERO, state)));
    }

    private static EffectDescriptor descriptor(Vec3 anchor, CompoundTag state) {
        return EffectDescriptor.builder(SESSION_ID, EffectKind.CINEMATIC, TIMELINE_ID, Level.OVERWORLD.location())
            .placement(new EffectTransform(new EffectPoint(anchor.x, anchor.y, anchor.z), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(7L, 0L, 200)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(state)
            .build();
    }
}
