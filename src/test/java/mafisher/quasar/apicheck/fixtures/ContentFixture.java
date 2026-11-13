package mafisher.quasar.apicheck.fixtures;

/** Test fixture: content may reference public API but must not reference engine implementation types. */
public final class ContentFixture {
    private ContentFixture() {
    }

    public static Object apiReference() {
        return mafisher.quasar.api.client.render.EffectMeshes.horizontalQuad();
    }

    public static Object engineReference() {
        return mafisher.quasar.client.effect.render.EffectRenderTypes.class;
    }
}
