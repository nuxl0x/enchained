package nux.enchained.item

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.item.Item
import net.minecraft.util.Rarity
import nux.enchained.util.IRegistrator
import nux.enchained.util.itypes.*

object BindingItems : IRegistrator() {

    val AGREEMENT: Item = register("agreement", AgreementItem(FabricItemSettings().maxCount(1).rarity(Rarity.COMMON)))
    val SIGNED_AGREEMENT: Item = register("signed_agreement", SignedAgreementItem(FabricItemSettings().maxCount(1).rarity(Rarity.COMMON)))

    val CONTRACT: Item = register("contract", ContractItem(FabricItemSettings().maxCount(1).rarity(Rarity.UNCOMMON)))
    val SIGNED_CONTRACT: Item = register("signed_contract", SignedContractItem(FabricItemSettings().maxCount(1).rarity(Rarity.UNCOMMON)))

    val CHARTER: Item = register("charter", CharterItem(FabricItemSettings().maxCount(1).rarity(Rarity.RARE)))
    val BOUND_CHARTER: Item = register("bound_charter", BoundCharterItem(FabricItemSettings().maxCount(1).rarity(Rarity.RARE)))

    val VOW: Item = register("vow", VowItem(FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)))
    val BOUND_VOW: Item = register("bound_vow", BoundVowItem(FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)))

    fun registerItems() {}
}