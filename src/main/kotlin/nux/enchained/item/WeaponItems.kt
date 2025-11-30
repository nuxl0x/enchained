package nux.enchained.item

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import nux.enchained.item.itypes.weapon.CharterBlade
import nux.enchained.item.itypes.weapon.ContractBlade
import nux.enchained.item.itypes.weapon.VowBlade
import nux.enchained.util.IRegistrator

object WeaponItems : IRegistrator() {

    val CONTRACTOR_BLADE = register("contractors_edge", ContractBlade(FabricItemSettings().maxCount(1)))
    val CHARTER_BLADE = register("dividing_charter", CharterBlade(FabricItemSettings().maxCount(1)))
    val VOW_BLADE = register("vows_end", VowBlade(FabricItemSettings().maxCount(1)))

    fun registerItems() {}

}