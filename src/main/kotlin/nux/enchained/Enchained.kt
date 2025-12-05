package nux.enchained

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import nux.enchained.chat.TeamChatFormatter
import nux.enchained.network.EnchainedNetworking
import nux.enchained.teams.TeamDamageHandler
import nux.enchained.teams.TeamManager
import nux.enchained.util.IHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Enchained : ModInitializer {
    const val MOD_ID: String = "enchained"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		LOGGER.info("Initializing Enchained.")
        IHelper.initializeItems()
        IHelper.initializeItemGroups()

        EnchainedNetworking.registerServerReceivers()
        TeamDamageHandler.register()
        TeamChatFormatter.register()

        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler: ServerPlayNetworkHandler?, sender: PacketSender?, server: MinecraftServer? ->
            val player = handler!!.player
            EnchainedNetworking.sendFullSyncTo(player)
        })
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val manager = TeamManager.get(server)
            LOGGER.info("Loaded ${manager.allTeams.size} teams")
        }
	}
}