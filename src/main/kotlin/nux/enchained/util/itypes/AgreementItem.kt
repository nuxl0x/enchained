package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems
import nux.enchained.util.ITooltips.Agreement
import nux.enchained.util.ITooltips.Misc

class AgreementItem(settings: Settings) : TwoPartyBindingItem(
    settings,
    "agreement",
    { BindingItems.SIGNED_AGREEMENT.defaultStack }
) {

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable(Agreement.ONE))
        tooltip.add(Text.translatable(Agreement.TWO))
        tooltip.add(Text.translatable(Agreement.THREE))
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            tooltip.add(Text.translatable(Misc.A_PRIM_USR, primaryUser).formatted(Formatting.GOLD))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}