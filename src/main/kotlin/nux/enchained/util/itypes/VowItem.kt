package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems
import nux.enchained.util.ITooltips.Vow
import nux.enchained.util.ITooltips.Misc

class VowItem(settings: Settings) : TwoPartyBindingItem(
    settings,
    "vow",
    { BindingItems.BOUND_VOW.defaultStack }
) {

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable(Vow.ONE))
        tooltip.add(Text.translatable(Vow.TWO))
        tooltip.add(Text.translatable(Vow.THREE))
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            tooltip.add(Text.translatable(Misc.PRIM_USR, primaryUser).formatted(Formatting.GOLD))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}