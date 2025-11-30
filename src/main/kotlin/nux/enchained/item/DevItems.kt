package nux.enchained.item

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import nux.enchained.util.IRegistrator
import nux.enchained.item.itypes.dev.GuiTester

object DevItems : IRegistrator() {

    val GUI_TESTER = register("gui_tester", GuiTester(FabricItemSettings().maxCount(1)))

    fun registerItems() {}
}