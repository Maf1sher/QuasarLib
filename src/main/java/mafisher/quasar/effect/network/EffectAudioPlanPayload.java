package mafisher.quasar.effect.network;

import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.audio.EffectAudioPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Server-to-client sound schedule for one effect instance.
 *
 * @param plan immutable cue schedule
 * @param serverGameTime server game time when this payload was created; clients use it to age the
 *     plan without trusting their own clock
 */
@ApiStatus.Internal
public record EffectAudioPlanPayload(EffectAudioPlan plan, long serverGameTime) implements CustomPacketPayload {
    public static final Type<EffectAudioPlanPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_audio_plan")
    );
    public static final StreamCodec<FriendlyByteBuf, EffectAudioPlanPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            EffectAudioPlanCodec.PLAN.encode(buffer, payload.plan());
            buffer.writeLong(payload.serverGameTime());
        },
        buffer -> new EffectAudioPlanPayload(EffectAudioPlanCodec.PLAN.decode(buffer), buffer.readLong())
    );

    public EffectAudioPlanPayload {
        Objects.requireNonNull(plan, "plan");
    }

    public EffectAudioPlanPayload(EffectAudioPlan plan) {
        this(plan, plan.startGameTime());
    }

    @Override
    public Type<EffectAudioPlanPayload> type() {
        return TYPE;
    }
}
