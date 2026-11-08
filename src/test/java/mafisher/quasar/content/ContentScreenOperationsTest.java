package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContentScreenOperationsTest {
    @Test
    void exposesContentOwnedOperationId() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_glow"),
            ContentScreenOperations.RUNE_GLOW.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentScreenOperations.BLACK_HOLE.id());
    }
}
