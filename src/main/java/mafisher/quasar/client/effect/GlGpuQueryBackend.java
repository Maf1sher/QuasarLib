package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

/** OpenGL implementation of {@link GpuQueryBackend} backed by {@code ARB_timer_query}. */
final class GlGpuQueryBackend implements GpuQueryBackend {
    private Boolean supported;

    @Override
    public boolean supported() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            return false;
        }
        if (this.supported == null) {
            this.supported = GL.getCapabilities().GL_ARB_timer_query;
        }
        return this.supported;
    }

    @Override
    public int beginQuery() {
        if (!supported()) {
            return 0;
        }
        // Never interfere with another mod's active query; a nested begin is a GL error.
        if (GL32C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL32C.GL_CURRENT_QUERY) != 0) {
            return 0;
        }
        int query = GL32C.glGenQueries();
        GL32C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        return query;
    }

    @Override
    public void endQuery(int query) {
        if (GL32C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL32C.GL_CURRENT_QUERY) == query) {
            GL32C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        }
    }

    @Override
    public boolean isResultAvailable(int query) {
        return GL32C.glGetQueryObjecti(query, GL32C.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    @Override
    public long resultNanos(int query) {
        return ARBTimerQuery.glGetQueryObjecti64(query, GL32C.GL_QUERY_RESULT);
    }

    @Override
    public void deleteQuery(int query) {
        if (query <= 0) {
            return;
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GL32C.glDeleteQueries(query);
        } else {
            RenderSystem.recordRenderCall(() -> GL32C.glDeleteQueries(query));
        }
    }
}
