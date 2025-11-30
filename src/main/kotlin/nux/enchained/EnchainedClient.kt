package nux.enchained

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import nux.enchained.client.gui.EnchainedTeamsScreen
import nux.enchained.network.EnchainedNetworking

class EnchainedClient : ClientModInitializer {

    companion object {

        var pendingScreen: Screen? = null

    }

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if(pendingScreen != null && client.currentScreen == null) {
                client.setScreen(pendingScreen)
                pendingScreen = null
            }
        }

        fun registerTeamGuiCommand() {

            ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
                dispatcher.register(
                    ClientCommandManager.literal("enchained")
                        .then(ClientCommandManager.literal("teamgui").executes { ctx ->
                            val client = MinecraftClient.getInstance()
                            if (client != null) {
                                pendingScreen = EnchainedTeamsScreen()
                            }
                            1
                        }
                        ))
            }

        }

        EnchainedNetworking.registerClientReceivers()
        registerTeamGuiCommand()

    }

}