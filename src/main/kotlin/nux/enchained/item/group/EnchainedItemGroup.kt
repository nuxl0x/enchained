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
import nux.enchained.item.ArtifactItems
import nux.enchained.item.BindingItems

object EnchainedItemGroup {

    // Declares Item Group Key
    val ENCHAINED_ITEM_GROUP_KEY: RegistryKey<ItemGroup> = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier(
        Enchained.MOD_ID, "enchained"))

    // Builds Item Group
    val ENCHAINED_ITEM_GROUP: ItemGroup = FabricItemGroup.builder()
        .icon { ItemStack(BindingItems.VOW) }
        .displayName(Text.translatable("itemGroup.enchained"))
        .build()

    // Function that registers the group
    fun registerItemGroup() {

        Registry.register(Registries.ITEM_GROUP, ENCHAINED_ITEM_GROUP_KEY, ENCHAINED_ITEM_GROUP)

    }

    // Adds items to group
    fun addItemGroupItems() {

        ItemGroupEvents.modifyEntriesEvent(ENCHAINED_ITEM_GROUP_KEY).register { itemGroup ->

            // Binding Items
            itemGroup.add(BindingItems.AGREEMENT)
            itemGroup.add(BindingItems.SIGNED_AGREEMENT)
            itemGroup.add(BindingItems.CONTRACT)
            itemGroup.add(BindingItems.SIGNED_CONTRACT)
            itemGroup.add(BindingItems.CHARTER)
            itemGroup.add(BindingItems.BOUND_CHARTER)
            itemGroup.add(BindingItems.VOW)
            itemGroup.add(BindingItems.BOUND_VOW)

            // Artifacts
            itemGroup.add(ArtifactItems.STRENGTH_ARTIFACT)
            itemGroup.add(ArtifactItems.SPEED_ARTIFACT)
            itemGroup.add(ArtifactItems.RESISTANCE_ARTIFACT)

        }

    }

    fun registerGroup() {
        registerItemGroup()
        addItemGroupItems()
    }

}