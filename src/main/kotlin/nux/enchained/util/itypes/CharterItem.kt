package nux.enchained.util.itypes

import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import nux.enchained.Enchained
import nux.enchained.data.DataInterface
import nux.enchained.item.BindingItems

class CharterItem(settings: Settings) : Item(settings) {

    fun updateInventory(user: PlayerEntity) {
        if (user is ServerPlayerEntity) {
            user.inventory.markDirty()
        }
    }

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {

        val stack: ItemStack = user.getStackInHand(hand)

        if (world.isClient) {
            return TypedActionResult.pass(stack)
        }

        val nbt: NbtCompound = stack.orCreateNbt
        val primaryUser: String = nbt.getString("primaryUser")


        // datatype, incredibly important
        val dataType = "charter"

        if (primaryUser.isEmpty()) {
            nbt.putString("primaryUser", user.entityName)
            stack.nbt = nbt
            updateInventory(user)
            return TypedActionResult.success(stack)
        }

        val secondaryUser: String = user.entityName

        // will only execute to here if primaryUser is already set
        nbt.putString("secondaryUser", user.entityName)
        stack.nbt = nbt
        updateInventory(user)

        val boundCharter: ItemStack = BindingItems.BOUND_CHARTER.defaultStack
        boundCharter.nbt = nbt

        // super important, gets class to interact with DataStorage
        val dInterface = DataInterface(world as ServerWorld, dataType, primaryUser, secondaryUser)
        val dataSuccess: Int = dInterface.create()

        if (dataSuccess != 0) {
            Enchained.LOGGER.error("dInterface returned non-zero value. ($dataSuccess)")
            return TypedActionResult.fail(stack)
        }

        user.setStackInHand(hand, boundCharter)

        return TypedActionResult.success(stack)
    }

    // adds cool tooltip to item
    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt: NbtCompound = stack.orCreateNbt

        tooltip.add(Text.translatable("itemTooltip.enchained.charter1"))
        tooltip.add(Text.translatable("itemTooltip.enchained.charter2"))
        tooltip.add(Text.translatable("itemTooltip.enchained.charter3"))

        // value updates from nbt
        if (nbt.contains("primaryUser")) {
            val primaryUser: String = nbt.getString("primaryUser")

            tooltip.add(Text.translatable("itemTooltip.enchained.primaryUser", primaryUser).formatted(Formatting.GOLD))
        }

        super.appendTooltip(stack, world, tooltip, context)

    }

}