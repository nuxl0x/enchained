package nux.enchained.item.itypes.binding

import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems
import nux.enchained.util.ITooltips.Contract
import nux.enchained.util.ITooltips.Misc

class ContractItem(settings: Settings) : TwoPartyBindingItem(
    settings,
    "contract",
    { BindingItems.SIGNED_CONTRACT.defaultStack }
) {

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable(Contract.ONE))
        tooltip.add(Text.translatable(Contract.TWO))
        tooltip.add(Text.translatable(Contract.THREE))
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            tooltip.add(Text.translatable(Misc.PRIM_USR, primaryUser).formatted(Formatting.GOLD))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}