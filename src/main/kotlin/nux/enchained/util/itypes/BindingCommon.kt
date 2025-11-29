package nux.enchained.util.itypes

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.Item.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import nux.enchained.Enchained
import nux.enchained.data.DataInterface
import nux.enchained.teams.TeamManager
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

        val dInterface = DataInterface(serverWorld, dataType, primaryName, signer.entityName)
        val dataSuccess = dInterface.create()
        if (dataSuccess != 0) {
            Enchained.LOGGER.error("dInterface returned non-zero value. ($dataSuccess)")
            return TypedActionResult.fail(stack)
        }

        user.setStackInHand(hand, signedStack)
        return TypedActionResult.success(signedStack)
    }
}

/**
 * Utility object for operating on the global graph of bound pairs and for
 * keeping bound items consistent with team changes.
 *
 * Responsibilities:
 * - Global 3-way binding detection.
 * - Inventory scanning to build an adjacency graph of all existing bindings.
 * - Automatic stripping of secondary bindings when team rules are violated,
 *   with optional conversion back to an "unsigned but bound" base item.
 */
object BindingGraphUtil {

    /**
     * Returns true if adding an undirected edge between [a] and [b] would create
     * a 3-way cycle of the form A–B, B–C, C–A.
     *
     * Steps:
     * - Scans all online players' inventories and collects existing bindings
     *   as undirected edges between primaryUuid and secondaryUuid.
     * - Builds an adjacency list over UUIDs.
     * - Checks if there exists some C where:
     *     C is neighbor of A and neighbor of B, with C != B.
     */
    fun wouldCreateThreeWay(server: MinecraftServer, a: UUID, b: UUID): Boolean {
        if (a == b) return false

        val adjacency = mutableMapOf<UUID, MutableSet<UUID>>()

        for (player in server.playerManager.playerList) {
            val inv = player.inventory
            for (stack in inv.main) collectEdge(stack, adjacency)
            for (stack in inv.armor) collectEdge(stack, adjacency)
            for (stack in inv.offHand) collectEdge(stack, adjacency)
        }

        val neighborsA = adjacency[a] ?: return false
        val neighborsB = adjacency[b] ?: return false

        for (v in neighborsA) {
            if (v != b && neighborsB.contains(v)) return true
        }

        return false
    }

    private fun collectEdge(stack: ItemStack, adjacency: MutableMap<UUID, MutableSet<UUID>>) {
        if (stack.isEmpty) return
        val nbt = stack.nbt ?: return
        if (!nbt.containsUuid("primaryUuid") || !nbt.containsUuid("secondaryUuid")) return

        val p = nbt.getUuid("primaryUuid")
        val s = nbt.getUuid("secondaryUuid")
        if (p == s) return

        adjacency.computeIfAbsent(p) { mutableSetOf() }.add(s)
        adjacency.computeIfAbsent(s) { mutableSetOf() }.add(p)
    }

    /**
     * Ensures that a signed binding item remains valid with respect to team rules.
     *
     * Behaviour:
     * - If primaryUuid or secondaryUuid is missing, does nothing.
     * - Looks up both players' teams via [TeamManager].
     * - If both exist and are the same team:
     *   - Removes "secondaryUser" and "secondaryUuid" from the stack NBT.
     *   - Invokes [toUnsigned] with the updated NBT to produce a replacement stack
     *     (typically the original base item type, e.g. CharterItem instead of BoundCharterItem).
     *   - If [owner] is non-null, replaces the item in the given [slot] with the
     *     returned stack and marks the inventory dirty.
     *
     * This is intended to be called from [Item.inventoryTick] on the signed items.
     *
     * Example usage from a signed item:
     *
     * BindingGraphUtil.clearSecondaryIfSameTeam(stack, world, owner, slot) { nbt ->
     *     val base = BindingItems.CHARTER.defaultStack
     *     base.nbt = nbt
     *     base
     * }
     */
    fun clearSecondaryIfSameTeam(
        stack: ItemStack,
        world: World,
        owner: ServerPlayerEntity?,
        slot: Int,
        toUnsigned: (NbtCompound) -> ItemStack
    ) {
        if (world.isClient) return
        val serverWorld = world as? ServerWorld ?: return

        val nbt = stack.nbt ?: return
        if (!nbt.containsUuid("primaryUuid") || !nbt.containsUuid("secondaryUuid")) return

        val manager = TeamManager.get(serverWorld.server)
        val primaryTeam = manager.getTeamOfPlayer(nbt.getUuid("primaryUuid")).orElse(null)
        val secondaryTeam = manager.getTeamOfPlayer(nbt.getUuid("secondaryUuid")).orElse(null)

        if (primaryTeam != null && secondaryTeam != null && primaryTeam == secondaryTeam) {
            nbt.remove("secondaryUser")
            nbt.remove("secondaryUuid")

            val newStack = toUnsigned(nbt.copy())

            if (owner != null) {
                owner.inventory.setStack(slot, newStack)
                owner.inventory.markDirty()
            } else {
                stack.nbt = nbt
            }
        }
    }
}