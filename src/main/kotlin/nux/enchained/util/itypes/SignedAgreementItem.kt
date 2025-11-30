package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.Entity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import nux.enchained.item.BindingItems
import nux.enchained.util.ITooltips.Agreement
import nux.enchained.util.ITooltips.Misc

class SignedAgreementItem(settings: Settings) : Item(settings) {

    override fun inventoryTick(stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean) {
        BindingGraphUtil.clearSecondaryIfSameTeam(
            stack,
            world,
            entity as? ServerPlayerEntity,
            slot
        ) { nbt ->
            val base = BindingItems.AGREEMENT.defaultStack
            base.nbt = nbt
            base
        }
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt
        tooltip.add(Text.translatable(Agreement.S_ONE))
        tooltip.add(Text.translatable(Agreement.S_TWO))
        if (nbt.contains("primaryUser") && nbt.contains("secondaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")
            val secondaryUser: String = nbt.getString("secondaryUser")
            tooltip.add(Text.translatable(Misc.A_PRIM_USR, primaryUser).formatted(Formatting.GOLD))
            tooltip.add(Text.translatable(Misc.A_SCND_USR, secondaryUser).formatted(Formatting.DARK_RED))
        }
        super.appendTooltip(stack, world, tooltip, context)
    }
}