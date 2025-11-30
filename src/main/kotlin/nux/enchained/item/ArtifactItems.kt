package nux.enchained.item

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.item.Item
import nux.enchained.item.itypes.artifact.ResistanceArtifact
import nux.enchained.item.itypes.artifact.SpeedArtifact
import nux.enchained.item.itypes.artifact.StrengthArtifact
import nux.enchained.util.IRegistrator

object ArtifactItems : IRegistrator() {

    val STRENGTH_ARTIFACT: Item = register("artifact_of_strength", StrengthArtifact(FabricItemSettings().maxCount(1)))
    val SPEED_ARTIFACT: Item = register("artifact_of_speed", SpeedArtifact(FabricItemSettings().maxCount(1)))
    val RESISTANCE_ARTIFACT: Item = register("artifact_of_resistance", ResistanceArtifact(FabricItemSettings().maxCount(1)))

    fun registerItems() {}

}