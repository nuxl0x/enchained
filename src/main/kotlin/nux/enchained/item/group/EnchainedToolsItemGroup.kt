package nux.enchained.item.group

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import nux.enchained.Enchained
import nux.enchained.item.WeaponItems

object EnchainedToolsItemGroup {

    // Declares Item Group Key
    val ENCHAINED_ITEM_GROUP_KEY: RegistryKey<ItemGroup> = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier(
        Enchained.MOD_ID, "enchained_tools"))

    // Builds Item Group
    val ENCHAINED_ITEM_GROUP: ItemGroup = FabricItemGroup.builder()
        .icon { ItemStack(WeaponItems.VOW_BLADE) }
        .displayName(Text.translatable("itemGroup.enchainedTools"))
        .build()

    // Function that registers the group
    fun registerItemGroup() {

        Registry.register(Registries.ITEM_GROUP, ENCHAINED_ITEM_GROUP_KEY, ENCHAINED_ITEM_GROUP)

    }

    // Adds items to group
    fun addItemGroupItems() {

        ItemGroupEvents.modifyEntriesEvent(ENCHAINED_ITEM_GROUP_KEY).register { itemGroup ->

            itemGroup.add(WeaponItems.CONTRACTOR_BLADE)
            itemGroup.add(WeaponItems.CHARTER_BLADE)
            itemGroup.add(WeaponItems.VOW_BLADE)

        }

    }

    fun registerGroup() {
        registerItemGroup()
        addItemGroupItems()
    }
}