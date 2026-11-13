package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClientAttachmentResolverTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "attachment_test");
    private static final ResourceLocation DIMENSION =
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void resolvesAnEntityParentAndUpdatesWhenTheParentMoves() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Map<UUID, EffectTransform> entities = new HashMap<>();
        entities.put(parentId, transform(10.0, 64.0, 0.0));
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            Map.of(childId, instance(childId, EffectAttachment.toEntity(
                parentId, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
            ))),
            frames(entities, Map.of(), false)
        );

        EffectTransform first = resolver.resolve(
            instance(childId, EffectAttachment.toEntity(
                parentId, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
            )),
            100L,
            0.0F
        );

        assertEquals(10.0, first.position().x(), 1.0E-6);
        assertEquals(65.0, first.position().y(), 1.0E-6);

        resolver.beginFrame();
        entities.put(parentId, transform(30.0, 64.0, 0.0));
        EffectTransform moved = resolver.resolve(
            instance(childId, EffectAttachment.toEntity(
                parentId, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
            )),
            101L,
            0.0F
        );

        assertEquals(30.0, moved.position().x(), 1.0E-6);
    }

    @Test
    void resolvesChainedEffectParents() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        Map<UUID, VisualEffectInstance> instances = new HashMap<>();
        instances.put(firstId, instance(firstId, null));
        instances.put(secondId, instance(secondId, EffectAttachment.toEffect(
            firstId, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
        )));
        instances.put(thirdId, instance(thirdId, EffectAttachment.toEffect(
            secondId, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
        )));
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            instances, frames(Map.of(), Map.of(), true)
        );

        EffectTransform resolved = resolver.resolve(instances.get(thirdId), 100L, 0.0F);

        assertEquals(7.0, resolved.position().x(), 1.0E-6);
        assertEquals(2.0, resolved.position().y(), 1.0E-6);
    }

    @Test
    void missingStopParentHidesTheEffectAndFreezeKeepsTheLastFrame() {
        UUID parentId = UUID.randomUUID();
        UUID stopId = UUID.randomUUID();
        UUID freezeId = UUID.randomUUID();
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            Map.of(
                stopId, instance(stopId, EffectAttachment.toEntity(
                    parentId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
                )),
                freezeId, instance(freezeId, EffectAttachment.toEntity(
                    parentId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
                ).withParentLossPolicy(EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST))
            ),
            frames(Map.of(), Map.of(), true)
        );

        assertNull(resolver.resolve(
            instance(stopId, EffectAttachment.toEntity(
                parentId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            )),
            100L,
            0.0F
        ));
        EffectTransform frozen = resolver.resolve(
            instance(freezeId, EffectAttachment.toEntity(
                parentId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ).withParentLossPolicy(EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST)),
            100L,
            0.0F
        );
        assertEquals(7.0, frozen.position().x(), 1.0E-6);
    }

    @Test
    void absentSubLevelProviderUsesTheAuthoredTransformEvenWithStopPolicy() {
        UUID childId = UUID.randomUUID();
        EffectAttachment attachment = EffectAttachment.toSubLevel(
            UUID.randomUUID(), new EffectPoint(7.0, 2.0, 3.0), 0.0F, 0.0F, 0.0F
        ).withParentLossPolicy(EffectAttachment.ParentLossPolicy.STOP_WITH_PARENT);
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            Map.of(childId, instance(childId, attachment)),
            frames(Map.of(), Map.of(), false)
        );

        EffectTransform resolved = resolver.resolve(
            instance(childId, attachment), 100L, 0.0F
        );

        assertEquals(7.0, resolved.position().x(), 1.0E-6);
        assertEquals(0.0, resolved.position().y(), 1.0E-6);
        assertEquals(0.0, resolved.position().z(), 1.0E-6);
    }

    @Test
    void resolvesAnAvailableSubLevelPose() {
        UUID childId = UUID.randomUUID();
        UUID subLevelId = UUID.randomUUID();
        EffectAttachment attachment = EffectAttachment.toSubLevel(
            subLevelId, new EffectPoint(1.0, 0.0, 0.0), 0.0F, 0.0F, 0.0F
        );
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            Map.of(childId, instance(childId, attachment)),
            frames(Map.of(), Map.of(subLevelId, transform(10.0, 20.0, 30.0)), true)
        );

        EffectTransform resolved = resolver.resolve(
            instance(childId, attachment), 100L, 0.0F
        );

        assertEquals(11.0, resolved.position().x(), 1.0E-6);
        assertEquals(20.0, resolved.position().y(), 1.0E-6);
        assertEquals(30.0, resolved.position().z(), 1.0E-6);
    }

    @Test
    void effectCycleIsHiddenInsteadOfRecursingForever() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Map<UUID, VisualEffectInstance> instances = new HashMap<>();
        instances.put(firstId, instance(firstId, EffectAttachment.toEffect(
            secondId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        )));
        instances.put(secondId, instance(secondId, EffectAttachment.toEffect(
            firstId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        )));
        ClientAttachmentResolver resolver = new ClientAttachmentResolver(
            instances, frames(Map.of(), Map.of(), true)
        );

        assertNull(resolver.resolve(instances.get(firstId), 100L, 0.0F));
    }

    private static ClientAttachmentResolver.ParentFrames frames(
        Map<UUID, EffectTransform> entities,
        Map<UUID, EffectTransform> subLevels,
        boolean subLevelAvailable
    ) {
        return new ClientAttachmentResolver.ParentFrames() {
            @Override
            public EffectTransform entity(UUID entityId, float partialTick) {
                return entities.get(entityId);
            }

            @Override
            public EffectTransform subLevel(UUID subLevelId, float partialTick) {
                return subLevels.get(subLevelId);
            }

            @Override
            public boolean subLevelAvailable() {
                return subLevelAvailable;
            }
        };
    }

    private static VisualEffectInstance instance(UUID instanceId, EffectAttachment attachment) {
        return new VisualEffectInstance(new StartVisualEffectPayload(
            EFFECT_ID,
            instanceId,
            DIMENSION,
            7.0,
            0.0,
            0.0,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            200,
            100L,
            100L,
            7L,
            null,
            attachment
        ));
    }

    private static EffectTransform transform(double x, double y, double z) {
        return new EffectTransform(new EffectPoint(x, y, z), 0.0F, 0.0F, 0.0F);
    }
}
