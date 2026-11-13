package mafisher.quasar.api.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class EffectParameterStateTest {
    @Test
    void buildsReplacesAndRemovesTypedValues() {
        EffectParameterState state = EffectParameterState.builder()
            .putFloat("fade", 1.0F)
            .putVec3("axis", 0.0F, 1.0F, 0.0F)
            .build();

        assertFalse(state.isEmpty());
        assertEquals(2, state.size());
        assertEquals(0.0F, state.value("axis").x(), 0.0F);
        assertEquals(1.0F, state.value("axis").y(), 0.0F);

        EffectParameterState replaced = state.with("fade", EffectCurve.Value.scalar(0.25F));
        assertEquals(0.25F, replaced.value("fade").x(), 0.0F);
        assertEquals(1.0F, state.value("fade").x(), 0.0F);

        EffectParameterState removed = replaced.without("axis");
        assertTrue(removed.has("fade"));
        assertFalse(removed.has("axis"));
        assertTrue(removed.without("missing") == removed);
    }

    @Test
    void mergesWithTheOtherStateWinning() {
        EffectParameterState left = EffectParameterState.of(
            "one", EffectCurve.Value.scalar(1.0F)
        );
        EffectParameterState right = EffectParameterState.of(
            "one", EffectCurve.Value.scalar(2.0F)
        ).with("two", EffectCurve.Value.vec2(3.0F, 4.0F));

        EffectParameterState merged = left.merge(right);

        assertEquals(2.0F, merged.value("one").x(), 0.0F);
        assertEquals(3.0F, merged.value("two").x(), 0.0F);
        assertSame(left, left.merge(EffectParameterState.EMPTY));
        assertSame(right, EffectParameterState.EMPTY.merge(right));
    }

    @Test
    void roundTripsEveryValueTypeThroughNbt() {
        EffectParameterState state = EffectParameterState.builder()
            .putFloat("float", 1.5F)
            .putVec2("vec2", 2.0F, 3.0F)
            .putVec3("vec3", 4.0F, 5.0F, 6.0F)
            .putColor("color", 0.1F, 0.2F, 0.3F, 0.4F)
            .build();

        CompoundTag tag = state.save();
        EffectParameterState loaded = EffectParameterState.load(tag);

        assertEquals(state.values(), loaded.values());
        assertEquals(EffectParameterState.SCHEMA_VERSION, tag.getInt("version"));
    }

    @Test
    void treatsAnEmptyTagAsNoOverrides() {
        assertSame(EffectParameterState.EMPTY, EffectParameterState.load(new CompoundTag()));
        assertSame(EffectParameterState.EMPTY,
            EffectParameterState.load(EffectParameterState.EMPTY.save()));
    }

    @Test
    void rejectsNewerSchemasAndInvalidTypes() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("version", EffectParameterState.SCHEMA_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> EffectParameterState.load(newer));

        CompoundTag invalid = new CompoundTag();
        invalid.putInt("version", 1);
        ListTag values = new ListTag();
        CompoundTag value = new CompoundTag();
        value.putString("name", "bad");
        value.putByte("type", (byte)99);
        values.add(value);
        invalid.put("values", values);

        assertThrows(IllegalArgumentException.class, () -> EffectParameterState.load(invalid));
    }

    @Test
    void rejectsBlankNamesAndUnknownLookups() {
        assertThrows(IllegalArgumentException.class,
            () -> EffectParameterState.builder().putFloat(" ", 1.0F));
        assertThrows(NullPointerException.class,
            () -> EffectParameterState.EMPTY.value(null));
    }

    @Test
    void exposesAnImmutableValueMap() {
        EffectParameterState state = EffectParameterState.of(
            "one", EffectCurve.Value.scalar(1.0F)
        );

        assertThrows(UnsupportedOperationException.class,
            () -> state.values().put("two", EffectCurve.Value.scalar(2.0F)));
        assertEquals(Map.of("one", EffectCurve.Value.scalar(1.0F)), state.values());
    }
}
