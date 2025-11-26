package nux.enchained.data

import net.minecraft.nbt.*
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState
import nux.enchained.Enchained

class ContractData : PersistentState() {

    val contracts: MutableList<String> = ArrayList()

    // holds static functions
    companion object {
        const val DATA_NAME = "enchained_contract_data"

        fun get(world: ServerWorld): ContractData {
            return world.persistentStateManager.getOrCreate(
                ::fromNbt,
                ::ContractData,
                DATA_NAME
            )
        }

        fun fromNbt(nbt: NbtCompound): ContractData {
            val data = ContractData()

            if (nbt.contains("contracts", NbtElement.LIST_TYPE.toInt())) {
                val contractsNbt: NbtList = nbt.getList("contracts", NbtElement.STRING_TYPE.toInt())
                for (i in 0 until contractsNbt.size) {
                    data.contracts.add(contractsNbt.getString(i))
                }
            }

            return data
        }

    }

    // nbt writer
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val contractsNbt = NbtList()

        for (str: String in contracts) {
            contractsNbt.add(NbtString.of(str))
        }
        nbt.put("contracts", contractsNbt)

        return nbt
    }

    // functions to actually interact with contract data
    // TODO: Add additional abstraction layer for data interaction.
    fun checkForEntry(query: String): Boolean {
        return contracts.contains(query)
    }

    fun createEntry(entryData: String) {

        if (!contracts.contains(entryData)) {
            contracts.add(entryData)
            markDirty()
            Enchained.LOGGER.info("Contract $entryData created successfully.")
        } else {
            Enchained.LOGGER.error("Contract $entryData already exists.")
        }

    }

    fun removeEntry(entryData: String) {

        if (contracts.contains(entryData)) {
            contracts.remove(entryData)
            markDirty()
            Enchained.LOGGER.info("Contract $entryData removed successfully.")
        } else {
            Enchained.LOGGER.error("Contract $entryData not found.")
        }

    }

}