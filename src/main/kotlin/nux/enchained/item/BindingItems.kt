package nux.enchained.item

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.item.Item
import net.minecraft.util.Rarity
import nux.enchained.util.IRegistrator
import nux.enchained.util.itypes.*

object BindingItems : IRegistrator() {

    val CONTRACT: Item = register("contract", ContractItem(FabricItemSettings().maxCount(1).rarity(Rarity.UNCOMMON)))
    val SIGNED_CONTRACT: Item = register("signed_contract", SignedContractItem(FabricItemSettings().maxCount(1).rarity(Rarity.UNCOMMON)))

    val CHARTER: Item = register("charter", CharterItem(FabricItemSettings().maxCount(1).rarity(Rarity.RARE)))
    val SIGNED_CHARTER: Item = register("signed_charter", SignedCharterItem(FabricItemSettings().maxCount(1).rarity(Rarity.RARE)))

    fun registerItems() {}
}