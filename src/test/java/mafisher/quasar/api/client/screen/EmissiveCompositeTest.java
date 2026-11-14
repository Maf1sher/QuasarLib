package mafisher.quasar.api.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EmissiveCompositeTest {
    private static final BloomSettings BLOOM = new BloomSettings(0.65F, 1.0F, 4.0F);

    @Test
    void defaultsKeepTheTierScaleAndDisableBloom() {
        EmissiveComposite composite = EmissiveComposite.builder().build();

        assertSame(EmissiveComposite.STANDARD_SHADER, composite.shader());
        assertEquals(1.0F, composite.emissiveScale());
        assertNull(composite.bloom());
        assertTrue(composite.emissionFog());
        assertEquals(0.0F, composite.solidity());
        assertEquals(0.0F, composite.compositionFloor());
    }

    @Test
    void standardShaderIsAnEngineAsset() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "emissive_composite"),
            EmissiveComposite.STANDARD_SHADER.id());
        assertEquals(DefaultVertexFormat.POSITION, EmissiveComposite.STANDARD_SHADER.format());
    }

    @Test
    void builderKeepsExplicitPolicy() {
        EmissiveComposite composite = EmissiveComposite.builder()
            .emissiveScale(0.5F)
            .bloom(BLOOM)
            .emissionFog(false)
            .solidity(0.75F)
            .compositionFloor(1.0F)
            .build();

        assertEquals(0.5F, composite.emissiveScale());
        assertSame(BLOOM, composite.bloom());
        assertFalse(composite.emissionFog());
        assertEquals(0.75F, composite.solidity());
        assertEquals(1.0F, composite.compositionFloor());
    }

    @Test
    void rejectsInvalidScaleSolidityAndFloor() {
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().emissiveScale(0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().emissiveScale(1.01F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().emissiveScale(Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().solidity(-0.01F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().solidity(1.01F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().solidity(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().compositionFloor(-0.01F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().compositionFloor(1.01F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.builder().compositionFloor(Float.NaN));
        assertThrows(NullPointerException.class,
            () -> EmissiveComposite.builder(null));
    }

    @Test
    void targetScaleIsAppliedOnce() {
        assertEquals(1920, EmissiveComposite.scaledSize(1920, 1.0F));
        assertEquals(960, EmissiveComposite.scaledSize(1920, 0.5F));
        assertEquals(1, EmissiveComposite.scaledSize(1, 0.1F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.scaledSize(0, 1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.scaledSize(100, 0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveComposite.scaledSize(100, 1.1F));
    }

    @Test
    void bloomPolicyFollowsQualityAndIntensity() {
        assertFalse(EmissiveComposite.bloomEnabled(null));
        assertFalse(EmissiveComposite.bloomEnabled(new BloomSettings(0.5F, 0.0F, 4.0F)));
        assertTrue(EmissiveComposite.bloomEnabled(BLOOM));
        assertFalse(EmissiveComposite.bloomUsesBlur(EffectQualityTier.LOW));
        assertTrue(EmissiveComposite.bloomUsesBlur(EffectQualityTier.MEDIUM));
        assertTrue(EmissiveComposite.bloomUsesBlur(EffectQualityTier.HIGH));
    }

    @Test
    void compositionAlphaSeparatesCoverageFromSolidity() {
        assertEquals(0.0F, EmissiveComposite.compositionAlpha(0.0F, 1.0F, 1.0F));
        assertEquals(0.0F, EmissiveComposite.compositionAlpha(1.0F, 0.0F, 1.0F));
        assertEquals(1.0F, EmissiveComposite.compositionAlpha(1.0F, 1.0F, 1.0F));
        assertEquals(0.5F, EmissiveComposite.compositionAlpha(1.0F, 1.0F, 0.5F));
        assertEquals(0.5F, EmissiveComposite.compositionAlpha(0.5F, 1.0F, 1.0F));
        assertEquals(0.0F, EmissiveComposite.compositionAlpha(Float.NaN, 1.0F, 1.0F));
    }
}
