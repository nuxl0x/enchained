package nux.enchained.util

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import nux.enchained.Enchained

open class IRegistrator {

    fun register(id: String, item: Item): Item {

        val itemID: Identifier = Identifier(Enchained.MOD_ID, id)
        return Registry.register(Registries.ITEM, itemID, item)

    }

}