package nux.enchained.util

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World
import nux.enchained.teams.TeamManager
import java.util.UUID

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