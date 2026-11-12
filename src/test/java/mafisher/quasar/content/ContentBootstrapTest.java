package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.junit.jupiter.api.Test;

class ContentBootstrapTest {
    @Test
    void contentOwnsItsModEntryPoint() {
        Mod annotation = ContentBootstrap.class.getAnnotation(Mod.class);

        assertNotNull(annotation);
        assertEquals(ContentBootstrap.MOD_ID, annotation.value());
        assertEquals("quasar_content", ContentBootstrap.MOD_ID);

        Mod clientAnnotation = ContentClientMod.class.getAnnotation(Mod.class);
        assertNotNull(clientAnnotation);
        assertEquals(ContentBootstrap.MOD_ID, clientAnnotation.value());
        assertArrayEquals(new Dist[] {Dist.CLIENT}, clientAnnotation.dist());
    }
}
