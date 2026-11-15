package mafisher.quasar.client.effect.inspect;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import mafisher.quasar.QuasarLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HUD panel for inspector text and the selected instance-buffer thumbnail. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
@ApiStatus.Internal
public final class InspectOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectOverlay.class);
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "inspect_buffer");
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int MAX_PANEL_WIDTH = 460;

    private static DynamicTexture texture;
    private static long lastUploadTick = Long.MIN_VALUE;
    private static int lastDraw = Integer.MIN_VALUE;

    private InspectOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        InspectController controller = InspectController.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!controller.enabled() || minecraft.options.hideGui) {
            return;
        }
        try {
            renderPanel(event.getGuiGraphics(), controller, minecraft);
        } catch (RuntimeException exception) {
            LOGGER.error("Inspector overlay failed; disabling the overlay", exception);
            controller.clear();
            clear();
        }
    }

    private static void renderPanel(GuiGraphics graphics, InspectController controller,
        Minecraft minecraft) {
        List<String> lines = controller.hudLines();
        if (lines.isEmpty()) {
            return;
        }
        int width = panelWidth(minecraft, lines);
        int height = lines.size() * 10 + PADDING * 2;
        graphics.fill(MARGIN, MARGIN, MARGIN + width, MARGIN + height, InspectStyle.PANEL);
        int y = MARGIN + PADDING;
        for (String line : lines) {
            int color = line.startsWith("  ") ? InspectStyle.TEXT_MUTED : InspectStyle.TEXT;
            if (line.contains("expired") || line.contains("run /quasarlib hitboxes")) {
                color = InspectStyle.TEXT_WARNING;
            }
            graphics.drawString(minecraft.font, line, MARGIN + PADDING, y, color, true);
            y += 10;
        }
        if (controller.category(InspectController.Category.BUFFERS)) {
            renderThumbnail(graphics, controller, minecraft);
        }
    }

    private static int panelWidth(Minecraft minecraft, List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, minecraft.font.width(line));
        }
        return Math.min(MAX_PANEL_WIDTH, width + PADDING * 2);
    }

    private static void renderThumbnail(GuiGraphics graphics, InspectController controller,
        Minecraft minecraft) {
        int selected = controller.selectedDraw();
        if (selected < 0) {
            return;
        }
        InspectFrame.DrawSlot draw = controller.frame().draw(selected);
        updateTexture(minecraft, draw, selected);
        if (texture == null) {
            return;
        }
        int x = graphics.guiWidth() - InspectBufferThumbnail.WIDTH - MARGIN;
        int y = MARGIN;
        graphics.fill(x - PADDING, y, x + InspectBufferThumbnail.WIDTH + PADDING,
            y + InspectBufferThumbnail.HEIGHT + 20, InspectStyle.PANEL);
        String title = "draw #" + selected + " " + draw.mode + " " + draw.instanceCount + " inst";
        graphics.drawString(minecraft.font, title, x, y + 2, InspectStyle.TEXT, true);
        graphics.blit(TEXTURE, x, y + 14, 0, 0.0F, 0.0F,
            InspectBufferThumbnail.WIDTH, InspectBufferThumbnail.HEIGHT,
            InspectBufferThumbnail.WIDTH, InspectBufferThumbnail.HEIGHT);
    }

    private static void updateTexture(Minecraft minecraft, InspectFrame.DrawSlot draw, int selected) {
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (texture != null && selected == lastDraw && tick - lastUploadTick < InspectController.REFRESH_TICKS) {
            return;
        }
        ensureTexture(minecraft);
        if (texture == null) {
            return;
        }
        int[] pixels = InspectBufferThumbnail.pixels(draw.rows, draw.rowCount);
        NativeImage image = texture.getPixels();
        for (int y = 0; y < InspectBufferThumbnail.HEIGHT; y++) {
            for (int x = 0; x < InspectBufferThumbnail.WIDTH; x++) {
                image.setPixelRGBA(x, y, pixels[y * InspectBufferThumbnail.WIDTH + x]);
            }
        }
        texture.upload();
        lastUploadTick = tick;
        lastDraw = selected;
    }

    private static void ensureTexture(Minecraft minecraft) {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(
            InspectBufferThumbnail.WIDTH, InspectBufferThumbnail.HEIGHT, false
        );
        texture.setFilter(false, false);
        minecraft.getTextureManager().register(TEXTURE, texture);
        lastDraw = Integer.MIN_VALUE;
    }

    /** Releases the thumbnail texture; called on unload, disconnect, and resource reload. */
    public static void clear() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
        lastUploadTick = Long.MIN_VALUE;
        lastDraw = Integer.MIN_VALUE;
    }
}
