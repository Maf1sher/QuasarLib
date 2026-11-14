package mafisher.quasar.api.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EffectDepthOcclusionTest {
    @Test
    void autoUsesRendererBoundsAndDisabledNeverCulls() {
        assertTrue(EffectDepthOcclusion.AUTO.enabled());
        assertNull(EffectDepthOcclusion.AUTO.bounds());
        assertFalse(EffectDepthOcclusion.DISABLED.enabled());
        assertNull(EffectDepthOcclusion.DISABLED.bounds());
    }

    @Test
    void explicitBoundsMustBeFiniteAndOrdered() {
        AABB bounds = new AABB(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);

        EffectDepthOcclusion occlusion = EffectDepthOcclusion.bounds(bounds);

        assertTrue(occlusion.enabled());
        assertEquals(bounds, occlusion.bounds());
        assertThrows(NullPointerException.class, () -> EffectDepthOcclusion.bounds(null));
        assertThrows(IllegalArgumentException.class,
            () -> EffectDepthOcclusion.bounds(new AABB(0.0, 0.0, 0.0, Double.NaN, 1.0, 1.0)));
    }

    @Test
    void disabledPolicyRejectsBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDepthOcclusion(false, new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)));
    }
}
