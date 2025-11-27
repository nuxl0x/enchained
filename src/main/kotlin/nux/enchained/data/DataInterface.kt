package nux.enchained.data

import net.minecraft.server.world.ServerWorld
import nux.enchained.Enchained

class DataInterface(
    world: ServerWorld,
    dataType: String,
    private val primaryUser: String,
    private val secondaryUser: String
) {
    val dataEntry = "$dataType-$primaryUser:$secondaryUser"
    val inverseDataEntry = "$dataType-$secondaryUser:$primaryUser"
    val dataStorage = DataStorage.get(world)

    fun create(): Int {
        // Checks if both users are the same person.
        if (primaryUser == secondaryUser) {
            Enchained.LOGGER.error("Entry '$dataEntry' cannot have matching fields.")
            return 1
        }

        // Checks if this entry already exists within the database.
        if (dataStorage.checkForEntry(dataEntry)) {
            return 1
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
}