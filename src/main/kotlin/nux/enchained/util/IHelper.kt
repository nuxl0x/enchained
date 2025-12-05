package nux.enchained.util

import nux.enchained.item.group.EnchainedItemGroup
import nux.enchained.item.group.EnchainedToolsItemGroup
import nux.enchained.item.ArtifactItems
import nux.enchained.item.BindingItems
import nux.enchained.item.DevItems
import nux.enchained.item.WeaponItems

object IHelper {

    // Item Initialization Function, called by Enchained.kt
    fun initializeItems() {
        BindingItems.registerItems()
        ArtifactItems.registerItems()
        WeaponItems.registerItems()
        DevItems.registerItems()
    }

    fun initializeItemGroups() {
        EnchainedItemGroup.registerGroup()
        EnchainedToolsItemGroup.registerGroup()
    }

}