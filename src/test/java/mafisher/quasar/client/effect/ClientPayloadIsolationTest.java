package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the common network path to types that exist on a dedicated server. */
class ClientPayloadIsolationTest {
    private static final Path MAIN_SOURCE_ROOT = findSourceRoot();
    private static final String NETWORK_SOURCE = "mafisher/quasar/effect/network/EffectNetwork.java";
    private static final String FACADE_SOURCE = "mafisher/quasar/client/effect/ClientEffectRuntime.java";

    @Test
    void payloadRegistrationDoesNotReferenceClientOnlyTypes() throws IOException {
        String source = Files.readString(MAIN_SOURCE_ROOT.resolve(NETWORK_SOURCE));

        for (String forbidden : List.of(
            "net.minecraft.client",
            "net.neoforged.neoforge.client",
            "ClientLevel",
            "RenderLevelStageEvent",
            "ClientEffectFrameRuntime",
            "ClientVisualEffectManager",
            "ClientScreenCompositor",
            "ClientEffectAudio"
        )) {
            assertFalse(source.contains(forbidden), "EffectNetwork must not reference " + forbidden);
        }
    }

    @Test
    void payloadFacadeImportsNoClientPackages() throws IOException {
        for (String line : Files.readAllLines(MAIN_SOURCE_ROOT.resolve(FACADE_SOURCE))) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            assertFalse(trimmed.contains("net.minecraft.client"),
                "ClientEffectRuntime must be safe to load on a dedicated server: " + trimmed);
            assertFalse(trimmed.contains("net.neoforged.neoforge.client"),
                "ClientEffectRuntime must be safe to load on a dedicated server: " + trimmed);
        }
    }

    @Test
    void payloadFacadeDescribesOnlyCommonTypes() throws ReflectiveOperationException {
        assertDescriptorsSafe(ClientEffectRuntime.class);
        assertDescriptorsSafe(Class.forName("mafisher.quasar.effect.network.EffectNetwork"));
    }

    private static void assertDescriptorsSafe(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertSafe(method.getReturnType(), type, method.getName());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertSafe(parameter, type, method.getName());
            }
        }
        for (Field field : type.getDeclaredFields()) {
            assertSafe(field.getType(), type, field.getName());
        }
    }

    private static void assertSafe(Class<?> referenced, Class<?> owner, String member) {
        if (referenced.isPrimitive()) {
            return;
        }
        if (referenced.isArray()) {
            assertSafe(referenced.getComponentType(), owner, member);
            return;
        }
        String name = referenced.getName();
        assertFalse(name.startsWith("net.minecraft.client."),
            owner.getSimpleName() + "." + member + " references " + name);
        assertFalse(name.startsWith("net.neoforged.neoforge.client."),
            owner.getSimpleName() + "." + member + " references " + name);
        assertFalse(name.equals("mafisher.quasar.client.effect.ClientEffectFrameRuntime"),
            owner.getSimpleName() + "." + member + " references client-only frame runtime");
    }

    private static Path findSourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("src/main/java");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            Path nested = current.resolve("quasar-lib/src/main/java");
            if (Files.isDirectory(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate src/main/java from " + Path.of("").toAbsolutePath());
    }
}
