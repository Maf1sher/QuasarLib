package mafisher.quasar.testing;

import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Shared specs, descriptors, and controllers for gameplay persistence tests. */
public final class GameplayEffectFixtures {
    public static final ResourceLocation TYPE =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_gameplay");
    public static final UUID SESSION =
        UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final BlockPos FIRST = new BlockPos(0, 64, 0);

    private GameplayEffectFixtures() {
    }

    public static GameplayEffectSpec spec() {
        return spec(EffectTickBudget.unlimited());
    }

    public static GameplayEffectSpec spec(EffectTickBudget budget) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("telegraph", 10), new EffectPhase("strike", 10)),
            3L,
            budget,
            OwnerPolicy.CONTINUE
        );
    }

    public static GameplayEffectSpec specWithPriority(EffectPriority priority) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("telegraph", 10), new EffectPhase("strike", 10)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE,
            priority
        );
    }

    public static GameplayEffectSpec ownedSpec(UUID ownerId, int durationTicks) {
        return new GameplayEffectSpec(
            TYPE,
            ownerId,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
    }

    public static GameplayEffectSpec destroyerSpec(int maxBlockChanges, int durationTicks) {
        return new GameplayEffectSpec(
            TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            3L,
            new EffectTickBudget(0, 0, 0, maxBlockChanges),
            OwnerPolicy.CONTINUE
        );
    }

    public static EffectDescriptor descriptor(GameplayEffectSpec spec, CompoundTag consumerState) {
        return EffectDescriptor.builder(
                SESSION,
                EffectKind.GAMEPLAY,
                TYPE,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
            )
            .placement(
                new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
                1.0F
            )
            .timing(spec.seed(), 0L, spec.phases().totalDurationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, consumerState))
            .build();
    }

    public static GameplayEffectHandle startDestroyer(
        TestServerLevel world,
        GameplayEffectSpec spec,
        GameplayEffectController controller
    ) {
        return GameplayEffectService.start(
            world.level(),
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(
                EffectCapabilities.destructiveCapability()
            )
        );
    }

    public static final class QueueController implements GameplayEffectController {
        private final BlockChange change;

        public QueueController(BlockChange change) {
            this.change = change;
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            context.queueBlockChange(this.change);
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    public static final class VisualController implements GameplayEffectController {
        @Override
        public void onStart(GameplayEffectContext context) {
            context.scope().playVisual(VisualEffectSpec.builder(
                    TYPE,
                    Vec3.ZERO,
                    100,
                    1L,
                    EffectAudience.nearby(64.0)
                )
                .rotation(0.0F, 0.0F, 0.0F)
                .scale(1.0F)
                .build());
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    public static final class RecordingController implements GameplayEffectController {
        public int starts;
        public int resumes;
        public int resumedPhase;

        @Override
        public void onStart(GameplayEffectContext context) {
            this.starts++;
        }

        @Override
        public void onResume(GameplayEffectContext context) {
            this.resumes++;
            this.resumedPhase = context.phaseIndex();
            context.updatePersistentState(
                state -> state.putInt("counter", state.getInt("counter") + 1)
            );
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            context.updatePersistentState(state -> state.putInt("counter", 1));
        }
    }
}
