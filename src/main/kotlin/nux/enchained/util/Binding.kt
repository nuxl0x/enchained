package nux.enchained.util

import net.minecraft.nbt.NbtCompound
import java.util.UUID

data class Binding(
    val type: String,
    val primaryUser: String,
    val primaryUuid: UUID,
    val secondaryUser: String,
    val secondaryUuid: UUID
) {

    fun toNbt(): NbtCompound {
        val nbt = NbtCompound()
        nbt.putString("type", type)
        nbt.putString("primaryUser", primaryUser)
        nbt.putUuid("primaryUuid", primaryUuid)
        nbt.putString("secondaryUser", secondaryUser)
        nbt.putUuid("secondaryUuid", secondaryUuid)
        return nbt
    }

    companion object {
        fun fromNbt(nbt: NbtCompound): Binding {
            return Binding(
                type = nbt.getString("type"),
                primaryUser = nbt.getString("primaryUser"),
                primaryUuid = nbt.getUuid("primaryUuid"),
                secondaryUser = nbt.getString("secondaryUser"),
                secondaryUuid = nbt.getUuid("secondaryUuid")
            )
        }
    }


}