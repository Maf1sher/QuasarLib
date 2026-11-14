package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Depth-only framebuffer that borrows the active scene depth texture without copying it.
 */
final class DepthProbeFramebuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DepthProbeFramebuffer.class);
    private static final AtomicBoolean loggedFailure = new AtomicBoolean();

    private int framebufferId = -1;
    private int attachedDepthTexture = -1;
    private int attachedWidth = -1;
    private int attachedHeight = -1;

    /** Attaches the target if needed and returns whether the framebuffer is complete. */
    boolean bind(RenderTarget target) {
        int depthTexture = target.getDepthTextureId();
        if (depthTexture <= 0) {
            return false;
        }
        if (this.framebufferId > 0 && this.attachedDepthTexture == depthTexture
            && this.attachedWidth == target.width && this.attachedHeight == target.height) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.framebufferId);
            if (GlStateManager.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)
                == GL30C.GL_FRAMEBUFFER_COMPLETE) {
                return true;
            }
            this.attachedDepthTexture = -1;
        }
        if (this.framebufferId <= 0) {
            this.framebufferId = GlStateManager.glGenFramebuffers();
            if (this.framebufferId <= 0) {
                return false;
            }
        }
        int attachment = attachmentFor(depthTexture);
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.framebufferId);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment,
            GL11.GL_TEXTURE_2D, depthTexture, 0);
        int status = GlStateManager.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            if (loggedFailure.compareAndSet(false, true)) {
                LOGGER.warn("Provider-depth occlusion cannot attach the active depth texture "
                    + "(status 0x{}); effects stay visible", Integer.toHexString(status));
            }
            this.attachedDepthTexture = -1;
            return false;
        }
        this.attachedDepthTexture = depthTexture;
        this.attachedWidth = target.width;
        this.attachedHeight = target.height;
        return true;
    }

    void delete() {
        if (this.framebufferId <= 0) {
            return;
        }
        int framebuffer = this.framebufferId;
        this.framebufferId = -1;
        this.attachedDepthTexture = -1;
        this.attachedWidth = -1;
        this.attachedHeight = -1;
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GlStateManager._glDeleteFramebuffers(framebuffer);
        } else {
            RenderSystem.recordRenderCall(() -> GlStateManager._glDeleteFramebuffers(framebuffer));
        }
    }

    private static int attachmentFor(int depthTexture) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(depthTexture);
            int internalFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0,
                GL11.GL_TEXTURE_INTERNAL_FORMAT);
            return switch (internalFormat) {
                case GL30C.GL_DEPTH_STENCIL, GL30C.GL_DEPTH24_STENCIL8,
                    GL30C.GL_DEPTH32F_STENCIL8 -> GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
                default -> GL30C.GL_DEPTH_ATTACHMENT;
            };
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }
    }
}
