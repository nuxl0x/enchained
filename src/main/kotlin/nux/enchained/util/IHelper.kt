package nux.enchained.util

import nux.enchained.group.EnchainedItemGroup
import nux.enchained.item.BindingItems

object IHelper {

    // Item Initialization Function, called by Enchained.kt
    fun initializeItems() {
        BindingItems.registerItems()
    }

    fun initializeItemGroups() {
        EnchainedItemGroup.registerGroup()
    }

}