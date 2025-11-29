package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Item.Settings
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems

class ContractItem(settings: Settings) : TwoPartyBindingItem(
    settings,
    "contract",
    { BindingItems.SIGNED_CONTRACT.defaultStack }
) {

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable("itemTooltip.enchained.contract1"))
        tooltip.add(Text.translatable("itemTooltip.enchained.contract2"))
        tooltip.add(Text.translatable("itemTooltip.enchained.contract3"))
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            tooltip.add(Text.translatable("itemTooltip.enchained.primaryUser", primaryUser).formatted(Formatting.GOLD))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}