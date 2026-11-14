package mafisher.quasar.testing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.mockito.Mockito;

/** Minimal resource manager backed by classpath assets or in-memory text. */
public final class TestResourceManager {
    private TestResourceManager() {
    }

    /** Resolves every request through {@code assets/<namespace>/<path>} on the test classpath. */
    public static ResourceManager fromClasspath() {
        return manager(location -> TestResourceManager.class.getResourceAsStream(
            "/assets/" + location.getNamespace() + "/" + location.getPath()
        ));
    }

    /** Serves the supplied text for the matching asset IDs and nothing else. */
    public static ResourceManager fromText(Map<ResourceLocation, String> files) {
        return manager(location -> {
            String text = files.get(location);
            return text == null
                ? null
                : new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        });
    }

    /** Serves one text asset and returns empty for every other ID. */
    public static ResourceManager fromText(ResourceLocation location, String text) {
        return fromText(Map.of(location, text));
    }

    /** Serves no resources. */
    public static ResourceManager missing() {
        return manager(location -> null);
    }

    private static ResourceManager manager(Function<ResourceLocation, InputStream> opener) {
        ResourceManager manager = Mockito.mock(ResourceManager.class);
        PackResources pack = Mockito.mock(PackResources.class);
        Mockito.when(manager.getResource(Mockito.any())).thenAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            InputStream stream = opener.apply(location);
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new Resource(pack, () -> stream));
        });
        return manager;
    }
}
