package nux.enchained.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import nux.enchained.client.gui.EnchainedTeamsScreen;

public class EnchainedClient implements ClientModInitializer {

    private static Screen pendingScreen = null;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingScreen != null && client.currentScreen == null) {
                client.setScreen(pendingScreen);
                pendingScreen = null;
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("enchainedteams")
                            .then(ClientCommandManager.literal("gui")
                                    .executes(ctx -> {
                                        MinecraftClient client = MinecraftClient.getInstance();
                                        if (client != null) {
                                            pendingScreen = new EnchainedTeamsScreen();
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }
}