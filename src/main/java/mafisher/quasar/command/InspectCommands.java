package mafisher.quasar.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.effect.network.InspectHitboxPayload;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.GameplayEffectRuntime;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Operator-only hitbox snapshot command. The client inspector draws authoritative server shapes,
 * so this command is the only path that publishes them and it sends only to the invoking player.
 */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
@ApiStatus.Internal
public final class InspectCommands {
    private static final int MAX_REPORTED_SESSIONS = 8;
    private static final int MAX_REPORTED_SHAPES = 4;

    private InspectCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarlib")
                .then(Commands.literal("hitboxes")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> inspectHitboxes(context.getSource())))
        );
    }

    private static int inspectHitboxes(CommandSourceStack source) {
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(source.getLevel());
        List<GameplayEffectRuntime.HitboxDebugSnapshot> snapshots = runtime == null
            ? List.of() : runtime.gameplay().hitboxSnapshots();
        if (snapshots.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active gameplay hitboxes."), false);
            return 1;
        }
        for (String line : reportLines(snapshots)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            InspectHitboxPayload payload = snapshotPayload(source, snapshots);
            runtime.packets().sendToPlayer(player, payload);
            source.sendSuccess(
                () -> Component.literal("Sent hitbox snapshot to " + player.getGameProfile().getName() + "."),
                false
            );
        }
        return 1;
    }

    private static InspectHitboxPayload snapshotPayload(CommandSourceStack source,
        List<GameplayEffectRuntime.HitboxDebugSnapshot> snapshots) {
        List<InspectHitboxPayload.Session> sessions = new ArrayList<>(snapshots.size());
        for (GameplayEffectRuntime.HitboxDebugSnapshot snapshot : snapshots) {
            List<InspectHitboxPayload.Shape> shapes = new ArrayList<>(snapshot.hitboxes().size());
            for (GameplayEffectRuntime.HitboxDebug hitbox : snapshot.hitboxes()) {
                shapes.add(new InspectHitboxPayload.Shape(hitbox.shape(), hitbox.outcome()));
            }
            sessions.add(new InspectHitboxPayload.Session(
                snapshot.sessionId(),
                snapshot.typeId(),
                snapshot.ageTicks(),
                snapshot.phaseIndex(),
                snapshot.phaseCount(),
                shapes
            ));
        }
        return new InspectHitboxPayload(
            source.getLevel().getGameTime(),
            source.getLevel().dimension().location(),
            sessions
        );
    }

    private static List<String> reportLines(List<GameplayEffectRuntime.HitboxDebugSnapshot> snapshots) {
        List<String> lines = new ArrayList<>();
        lines.add("Hitboxes: " + snapshots.size() + " session(s)");
        int sessions = Math.min(snapshots.size(), MAX_REPORTED_SESSIONS);
        for (int index = 0; index < sessions; index++) {
            GameplayEffectRuntime.HitboxDebugSnapshot snapshot = snapshots.get(index);
            lines.add("  " + snapshot.typeId() + " " + shortId(snapshot.sessionId())
                + " age=" + snapshot.ageTicks()
                + " phase=" + (snapshot.phaseIndex() + 1) + "/" + snapshot.phaseCount());
            int shapes = Math.min(snapshot.hitboxes().size(), MAX_REPORTED_SHAPES);
            for (int shapeIndex = 0; shapeIndex < shapes; shapeIndex++) {
                lines.add("    " + shapeLine(snapshot.hitboxes().get(shapeIndex)));
            }
            if (snapshot.hitboxes().size() > shapes) {
                lines.add("    ... +" + (snapshot.hitboxes().size() - shapes) + " shape(s)");
            }
        }
        if (snapshots.size() > sessions) {
            lines.add("  ... +" + (snapshots.size() - sessions) + " session(s)");
        }
        return lines;
    }

    private static String shapeLine(GameplayEffectRuntime.HitboxDebug hitbox) {
        EffectDamageShape shape = hitbox.shape();
        return hitbox.outcome() + " " + describe(shape);
    }

    private static String describe(EffectDamageShape shape) {
        if (shape instanceof EffectDamageShape.Box box) {
            return "box " + bounds(box);
        }
        if (shape instanceof EffectDamageShape.Sphere sphere) {
            return "sphere center=" + point(sphere.center().x(), sphere.center().y(), sphere.center().z())
                + " r=" + decimal(sphere.radius());
        }
        if (shape instanceof EffectDamageShape.Cylinder cylinder) {
            return "cylinder base=" + point(cylinder.base().x(), cylinder.base().y(), cylinder.base().z())
                + " r=" + decimal(cylinder.radius()) + " h=" + decimal(cylinder.height());
        }
        if (shape instanceof EffectDamageShape.Beam beam) {
            return "beam " + point(beam.start().x(), beam.start().y(), beam.start().z())
                + " -> " + point(beam.end().x(), beam.end().y(), beam.end().z())
                + " r=" + decimal(beam.radius());
        }
        return shape.getClass().getSimpleName();
    }

    private static String bounds(EffectDamageShape.Box box) {
        return point(box.bounds().minX(), box.bounds().minY(), box.bounds().minZ())
            + " -> " + point(box.bounds().maxX(), box.bounds().maxY(), box.bounds().maxZ());
    }

    private static String point(double x, double y, double z) {
        return "(" + decimal(x) + "," + decimal(y) + "," + decimal(z) + ")";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
