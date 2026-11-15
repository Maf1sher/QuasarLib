package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.junit.jupiter.api.Test;

class ClientEffectCommandsTest {
    @Test
    void registersTheClientLocalReloadCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        ClientEffectCommands.register(new RegisterClientCommandsEvent(
            dispatcher, mock(CommandBuildContext.class)
        ));

        assertNotNull(dispatcher.getRoot().getChild("quasarlib"));
        assertNotNull(dispatcher.getRoot().getChild("quasarlib").getChild("reload"));
    }
}
