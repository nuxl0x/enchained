package nux.enchained.data

import net.minecraft.nbt.*
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState
import nux.enchained.Enchained

class DataStorage : PersistentState() {

    val storedData: MutableList<String> = ArrayList()

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
                val dataNbt: NbtList = nbt.getList("enchained_dat", NbtElement.STRING_TYPE.toInt())
                for (i in 0 until dataNbt.size) {
                    data.storedData.add(dataNbt.getString(i))
                }
            }

            return data
        }

    }

    // nbt writer
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val dataNbt = NbtList()

        for (str: String in storedData) {
            dataNbt.add(NbtString.of(str))
        }
        nbt.put("enchained_dat", dataNbt)

        return nbt
    }

    // functions to actually interact with data
    // do NOT directly interact with these
    fun checkForEntry(query: String): Boolean {
        return storedData.contains(query)
    }

    fun createEntry(entryData: String) {

            storedData.add(entryData)
            markDirty()
            Enchained.LOGGER.info("Entry '$entryData' created successfully.")

    }

    fun removeEntry(entryData: String) {

        if (storedData.contains(entryData)) {
            storedData.remove(entryData)
            markDirty()
            Enchained.LOGGER.info("Entry $entryData removed successfully.")
        } else {
            Enchained.LOGGER.error("Entry $entryData not found.")
        }

    }

}