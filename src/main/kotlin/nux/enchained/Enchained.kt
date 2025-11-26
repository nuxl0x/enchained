package nux.enchained

import net.fabricmc.api.ModInitializer
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
	}
}