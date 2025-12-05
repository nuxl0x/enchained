package nux.enchained.data

import net.minecraft.server.world.ServerWorld
import nux.enchained.Enchained
import nux.enchained.util.Binding
import java.util.UUID

class DataInterface(
    world: ServerWorld,
    dataType: String,
    primaryUser: String,
    primaryUuid: UUID,
    secondaryUser: String,
    secondaryUuid: UUID
) {
    val dataEntry = Binding(dataType, primaryUser, primaryUuid, secondaryUser, secondaryUuid)
    val inverseDataEntry = Binding(dataType, secondaryUser, secondaryUuid, primaryUser, primaryUuid)
    val dataStorage = DataStorage.get(world)

    fun create(): Int {
        // Checks if both users are the same person.
//        if (primaryUser == secondaryUser) {
//            Enchained.LOGGER.error("Entry '$dataEntry' cannot have matching fields.")
//            return 1
//        }

        // Checks if this entry already exists within the database.
        if (dataStorage.checkForEntry(dataEntry)) {
            return 0
        }

        // Checks if the inverse of this entry already exists within the database.
        if (dataStorage.checkForEntry(inverseDataEntry)) {
            return 1
        }

        // Creation call to make new entry.
        dataStorage.createEntry(dataEntry)
        return 0
    }

    fun remove() {
        dataStorage.removeEntry(dataEntry)
    }

    fun check(): Boolean {
        val returnValue: Boolean = dataStorage.checkForEntry(dataEntry)

        return returnValue
    }

    fun getAll(): MutableList<Binding> {
        val allData: MutableList<Binding> = dataStorage.getEntries()
        return allData
    }

}