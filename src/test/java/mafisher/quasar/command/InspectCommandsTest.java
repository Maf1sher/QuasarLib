package mafisher.quasar.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

class InspectCommandsTest {
    @Test
    void registersTheOperatorHitboxSnapshotCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        InspectCommands.register(new RegisterCommandsEvent(
            dispatcher, Commands.CommandSelection.DEDICATED, mock(CommandBuildContext.class)
        ));

        assertNotNull(dispatcher.getRoot().getChild("quasarlib"));
        assertNotNull(dispatcher.getRoot().getChild("quasarlib").getChild("hitboxes"));
    }
}
