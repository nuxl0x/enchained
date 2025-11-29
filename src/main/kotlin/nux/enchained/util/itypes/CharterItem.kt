package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Item.Settings
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems

class CharterItem(settings: Settings) : TwoPartyBindingItem(
    settings,
    "charter",
    { BindingItems.BOUND_CHARTER.defaultStack }
) {

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable("itemTooltip.enchained.charter1"))
        tooltip.add(Text.translatable("itemTooltip.enchained.charter2"))
        tooltip.add(Text.translatable("itemTooltip.enchained.charter3"))
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            tooltip.add(Text.translatable("itemTooltip.enchained.primaryUser", primaryUser).formatted(Formatting.GOLD))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}