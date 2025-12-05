package nux.enchained.data

import net.minecraft.nbt.*
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState
import nux.enchained.Enchained
import nux.enchained.util.Binding

class DataStorage : PersistentState() {

    val storedData: MutableList<Binding> = ArrayList()

    // holds static functions
    companion object {
        const val DATA_NAME = "enchained_data"

        fun get(world: ServerWorld): DataStorage {
            return world.persistentStateManager.getOrCreate(
                ::fromNbt,
                ::DataStorage,
                DATA_NAME
            )
        }

        fun fromNbt(nbt: NbtCompound): DataStorage {
            val data = DataStorage()

            if (nbt.contains("enchained_dat", NbtElement.LIST_TYPE.toInt())) {
                val dataNbt: NbtList = nbt.getList("enchained_dat", NbtElement.COMPOUND_TYPE.toInt())
                for (i in 0 until dataNbt.size) {
                    data.storedData.add(Binding.fromNbt(dataNbt.getCompound(i)))
                }
            }

            return data
        }

    }

    // nbt writer
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val dataNbt = NbtList()

        for (binding: Binding in storedData) {
            dataNbt.add(binding.toNbt())
        }
        nbt.put("enchained_dat", dataNbt)

        return nbt
    }

    // functions to actually interact with data
    // do NOT directly interact with these
    fun checkForEntry(query: Binding): Boolean {
        return storedData.contains(query)
    }

    fun createEntry(entryData: Binding) {

            storedData.add(entryData)
            markDirty()
            Enchained.LOGGER.info("Entry '$entryData' created successfully.")

    }

    fun removeEntry(entryData: Binding) {

        if (storedData.contains(entryData)) {
            storedData.remove(entryData)
            markDirty()
            Enchained.LOGGER.info("Entry $entryData removed successfully.")
        } else {
            Enchained.LOGGER.error("Entry $entryData not found.")
        }

    }

    fun getEntries(): MutableList<Binding> {
        return storedData
    }

}