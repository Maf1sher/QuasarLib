package mafisher.quasar.client.effect.render;

import mafisher.quasar.api.client.render.EffectMesh;
import net.minecraft.client.renderer.RenderType;

/** One instanced material batch without the per-frame payload. */
interface InstanceBatchDescriptor {
    String id();

    RenderType renderType();

    EffectMesh mesh();

    int overlay();

    int light();
}
