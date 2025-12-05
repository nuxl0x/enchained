package nux.enchained.item.itypes.binding

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import nux.enchained.Enchained
import nux.enchained.data.DataInterface
import nux.enchained.teams.TeamManager
import nux.enchained.util.BindingGraphUtil
import java.util.UUID

/**
 * Base item for all two-party binding items (agreement, charter, contract, vow).
 *
 * Behaviour:
 * - First use: binds the item to the user as the primary party.
 *   - Stores both the display name ("primaryUser") and UUID ("primaryUuid").
 * - Second use: attempts to sign the item with a second player as the secondary party.
 *   - Rejects signing if:
 *     - Both players are on the same team, or
 *     - Adding this edge would create a 3-way binding cycle (A–B, B–C, then C–A).
 *   - On success:
 *     - Writes "secondaryUser" and "secondaryUuid".
 *     - Creates a backing data entry through [DataInterface].
 *     - Replaces the current stack with a "signed" item provided by [signedStackSupplier].
 */
abstract class TwoPartyBindingItem(
    settings: Settings,
    private val dataType: String,
    private val signedStackSupplier: () -> ItemStack
) : Item(settings) {

    /**
     * Handles both the initial bind (primary) and the subsequent sign (secondary).
     *
     * The method runs only on the logical server and will:
     * - On first use, set the primary player data on the current stack.
     * - On second use, validate teams and global binding graph, then:
     *   - Persist the binding via [DataInterface].
     *   - Replace the stack in-hand with the signed variant.
     */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (world.isClient) return TypedActionResult.pass(stack)

        val serverWorld = world as? ServerWorld ?: return TypedActionResult.pass(stack)
        val nbt = stack.orCreateNbt
        val primaryName = nbt.getString("primaryUser")

        if (primaryName.isEmpty()) {
            val serverPlayer = user as? ServerPlayerEntity ?: return TypedActionResult.pass(stack)
            nbt.putString("primaryUser", serverPlayer.entityName)
            nbt.putUuid("primaryUuid", serverPlayer.uuid)
            stack.nbt = nbt
            serverPlayer.inventory.markDirty()
            return TypedActionResult.success(stack)
        }

        val signer = user as? ServerPlayerEntity ?: return TypedActionResult.pass(stack)

        var primaryUuid: UUID? =
            if (nbt.containsUuid("primaryUuid")) nbt.getUuid("primaryUuid") else null

        if (primaryUuid == null && primaryName.isNotEmpty()) {
            val match = serverWorld.server.playerManager.playerList.firstOrNull { it.entityName == primaryName }
            if (match != null) {
                primaryUuid = match.uuid
                nbt.putUuid("primaryUuid", primaryUuid)
                stack.nbt = nbt
                signer.inventory.markDirty()
            }
        }

        if (primaryUuid == null) {
            return TypedActionResult.fail(stack)
        }

        val manager = TeamManager.get(serverWorld.server)
        val primaryTeam = manager.getTeamOfPlayer(primaryUuid).orElse(null)
        val secondaryTeam = manager.getTeamOfPlayer(signer.uuid).orElse(null)

        if (primaryTeam != null && secondaryTeam != null && primaryTeam == secondaryTeam) {
            signer.sendMessage(Text.translatable("message.enchained.binding.same_team"), false)
            return TypedActionResult.fail(stack)
        }

        if (BindingGraphUtil.wouldCreateThreeWay(serverWorld.server, primaryUuid, signer.uuid)) {
            signer.sendMessage(Text.translatable("message.enchained.binding.three_way_forbidden"), false)
            return TypedActionResult.fail(stack)
        }

        nbt.putString("secondaryUser", signer.entityName)
        nbt.putUuid("secondaryUuid", signer.uuid)
        stack.nbt = nbt
        signer.inventory.markDirty()

        val signedStack = signedStackSupplier()
        signedStack.nbt = nbt.copy()

        val dInterface = DataInterface(serverWorld, dataType, primaryName, primaryUuid, signer.entityName, signer.uuid)
        val dataSuccess = dInterface.create()
        if (dataSuccess != 0) {
            Enchained.LOGGER.error("dInterface returned non-zero value. ($dataSuccess)")
            return TypedActionResult.fail(stack)
        }

        user.setStackInHand(hand, signedStack)
        return TypedActionResult.success(signedStack)
    }
}
