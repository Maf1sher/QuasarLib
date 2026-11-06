package mafisher.quasar.effect.cinematic;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Loads datapack timelines from {@code data/<namespace>/quasar/cinematics/<path>.json}. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CinematicTimelineReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "quasar/cinematics";

    public CinematicTimelineReloadListener() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new CinematicTimelineReloadListener());
    }

    @Override
    protected void apply(
        Map<ResourceLocation, JsonElement> files,
        ResourceManager resourceManager,
        ProfilerFiller profiler
    ) {
        CinematicTimelineRegistry.reload(files);
    }
}
