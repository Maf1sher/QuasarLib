package mafisher.quasar.apicheck.fixtures;

/** Test fixture: engine implementation must not reference content classes or strings. */
public final class EngineFixture {
    private EngineFixture() {
    }

    public static Object contentReference() {
        return mafisher.quasar.content.ContentEffectIds.RUNE_CIRCLE;
    }
}
