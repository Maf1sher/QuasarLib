package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One streaming RGBA32F buffer texture for instanced effect payloads. The texture and buffer are created lazily on
 * the render thread, reused across frames, and restored to their previous binding after each draw.
 */
final class InstanceBufferTexture implements AutoCloseable {
    static final int TEXTURE_UNIT = 7;

    private static final int INITIAL_INSTANCES = 128;
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceBufferTexture.class);

    private int bufferId;
    private int textureId;
    private int capacity;
    private int maxInstances = -1;
    private boolean failureLogged;
    private FloatBuffer staging;
    private int previousActiveTexture;
    private int previousBufferTexture;
    private boolean bound;

    /** Returns whether this client can use buffer textures; cached after the first render-thread query. */
    boolean isSupported() {
        if (this.maxInstances >= 0) {
            return this.maxInstances > 0;
        }
        try {
            if (!GL.getCapabilities().OpenGL31) {
                this.maxInstances = 0;
                return false;
            }
            int maxTexels = GL11C.glGetInteger(GL31C.GL_MAX_TEXTURE_BUFFER_SIZE);
            this.maxInstances = Math.max(0, maxTexels / InstanceBufferLayout.TEXELS_PER_INSTANCE);
        } catch (RuntimeException exception) {
            this.maxInstances = 0;
            logFailure("query buffer texture capabilities", exception);
        }
        return this.maxInstances > 0;
    }

    /** Returns the largest batch the driver can hold in one buffer texture. */
    int maxInstances() {
        return isSupported() ? this.maxInstances : 0;
    }

    /**
     * Uploads one batch and binds the buffer texture on the engine's reserved unit.
     *
     * @return {@code true} when the texture is bound and the sampler was pointed at it
     */
    boolean bind(int samplerLocation, List<InstancedBatchBuilder.Instance> instances,
        int start, int end, String passId) {
        Objects.requireNonNull(instances, "instances");
        if (start < 0 || end < start || end > instances.size()) {
            throw new IllegalArgumentException("Invalid instance range");
        }
        if (samplerLocation < 0 || start == end || !isSupported()) {
            return false;
        }
        if (!ensureResources() || !upload(instances, start, end, passId)) {
            return false;
        }

        int previousActive = GlStateManager._getActiveTexture();
        int previousTexture = 0;
        try {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + TEXTURE_UNIT);
            previousTexture = GL11C.glGetInteger(GL31C.GL_TEXTURE_BINDING_BUFFER);
            GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, this.textureId);
            GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, GL30C.GL_RGBA32F, this.bufferId);
            GL20C.glUniform1i(samplerLocation, TEXTURE_UNIT);
        } catch (RuntimeException exception) {
            restoreTexture(previousActive, previousTexture);
            logFailure("bind buffer texture for pass " + passId, exception);
            return false;
        }

        this.previousActiveTexture = previousActive;
        this.previousBufferTexture = previousTexture;
        this.bound = true;
        return true;
    }

    /** Restores the texture unit and buffer-texture binding saved by {@link #bind}. */
    void unbind() {
        if (!this.bound) {
            return;
        }
        this.bound = false;
        restoreTexture(this.previousActiveTexture, this.previousBufferTexture);
    }

    private void restoreTexture(int activeTexture, int bufferTexture) {
        GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, bufferTexture);
        GlStateManager._activeTexture(activeTexture);
    }

    private boolean ensureResources() {
        if (this.textureId != 0 && this.bufferId != 0) {
            return true;
        }
        try {
            this.textureId = GlStateManager._genTexture();
            this.bufferId = GlStateManager._glGenBuffers();
            if (this.textureId == 0 || this.bufferId == 0) {
                logFailure("create buffer texture", null);
                close();
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            logFailure("create buffer texture", exception);
            close();
            return false;
        }
    }

    private boolean upload(List<InstancedBatchBuilder.Instance> instances, int start, int end,
        String passId) {
        int count = end - start;
        if (!ensureCapacity(count)) {
            return false;
        }
        FloatBuffer buffer = this.staging;
        buffer.clear();
        InstanceBufferLayout.write(buffer, instances, start, end);
        buffer.flip();

        GlStateManager._glBindBuffer(GL31C.GL_TEXTURE_BUFFER, this.bufferId);
        try {
            GL15C.glBufferData(GL31C.GL_TEXTURE_BUFFER, InstanceBufferLayout.byteSize(count),
                GL15C.GL_STREAM_DRAW);
            GL15C.glBufferSubData(GL31C.GL_TEXTURE_BUFFER, 0L, buffer);
        } catch (RuntimeException exception) {
            logFailure("upload buffer texture for pass " + passId, exception);
            return false;
        } finally {
            GlStateManager._glBindBuffer(GL31C.GL_TEXTURE_BUFFER, 0);
        }
        return true;
    }

    private boolean ensureCapacity(int instances) {
        if (instances <= this.capacity) {
            return true;
        }
        int nextCapacity = Math.max(instances, Math.max(INITIAL_INSTANCES, this.capacity * 2));
        try {
            FloatBuffer next = MemoryUtil.memAllocFloat(
                InstanceBufferLayout.floatCount(nextCapacity)
            );
            if (this.staging != null) {
                MemoryUtil.memFree(this.staging);
            }
            this.staging = next;
            this.capacity = nextCapacity;
            return true;
        } catch (RuntimeException | OutOfMemoryError exception) {
            logFailure("allocate buffer texture staging", exception);
            return false;
        }
    }

    private void logFailure(String action, Throwable exception) {
        if (this.failureLogged) {
            return;
        }
        this.failureLogged = true;
        if (exception == null) {
            LOGGER.warn("Could not {}; falling back to uniform instancing", action);
        } else {
            LOGGER.warn("Could not {}; falling back to uniform instancing", action, exception);
        }
    }

    @Override
    public void close() {
        unbind();
        if (this.textureId != 0) {
            GlStateManager._deleteTexture(this.textureId);
            this.textureId = 0;
        }
        if (this.bufferId != 0) {
            GlStateManager._glDeleteBuffers(this.bufferId);
            this.bufferId = 0;
        }
        if (this.staging != null) {
            MemoryUtil.memFree(this.staging);
            this.staging = null;
        }
        this.capacity = 0;
        this.bound = false;
    }
}
