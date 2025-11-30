package nux.enchained.datagen

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import java.util.Locale.getDefault

class EnglishLanguageProvider(
    dataOutput: FabricDataOutput,
) : FabricLanguageProvider(dataOutput, "en_us") {

    val allModItems = Registries.ITEM.ids
        .filter { it.namespace == "enchained" }
        .mapNotNull { Registries.ITEM.get(it) }

    fun formatItemName(item: Item): String {
        val id = Registries.ITEM.getId(item).path
        return id.split("_")
            .joinToString(" ") { it ->
                it.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() } }
    }



    fun agreement(tBuilder: TranslationBuilder) {
        // Unbound Tooltips
        tBuilder.add("itemTooltip.enchained.agreement1", "Use in order to propose or acknowledge an agreement.")
        tBuilder.add("itemTooltip.enchained.agreement2", "Doing so will bind your name to this item.")
        tBuilder.add("itemTooltip.enchained.agreement3", "If undone, the agreement will be null.")

        // Bound Tooltips
        tBuilder.add("itemTooltip.enchained.sAgreement1", "Represents an agreement between two signatories.")
        tBuilder.add("itemTooltip.enchained.sAgreement2", "You are able to see the names below.")
    }

    fun contract(tBuilder: TranslationBuilder) {
        // Unbound Tooltips
        tBuilder.add("itemTooltip.enchained.contract1", "Use this item to sign this contract.")
        tBuilder.add("itemTooltip.enchained.contract2", "Signing this contract will temporarily bind your soul to it.")
        tBuilder.add("itemTooltip.enchained.contract3", "This allows you to empower yourself, at a cost.")

        // Bound Tooltips
        tBuilder.add("itemTooltip.enchained.sContract1", "Represents a contract between two people.")
        tBuilder.add("itemTooltip.enchained.sContract2", "You are able to see the signatories below.")

    }

    fun charter(tBuilder: TranslationBuilder) {
        // Unbound Tooltips
        tBuilder.add("itemTooltip.enchained.charter1", "A powerful agreement.")
        tBuilder.add("itemTooltip.enchained.charter2", "An indestructible bond.")
        tBuilder.add("itemTooltip.enchained.charter3", "By all means.")

        // Bound Tooltips
        tBuilder.add("itemTooltip.enchained.bCharter1", "An agreement, nearly irreversible.")
        tBuilder.add("itemTooltip.enchained.bCharter2", "A bond that will last beyond the end.")

    }

    fun vow(tBuilder: TranslationBuilder) {
        // Unbound Tooltips
        tBuilder.add("itemTooltip.enchained.vow1", "Inconceivably strong.")
        tBuilder.add("itemTooltip.enchained.vow2", "A vow that cannot be undone.")
        tBuilder.add("itemTooltip.enchained.vow3", "Through any means.")

        // Bound Tooltips
        tBuilder.add("itemTooltip.enchained.bVow1", "An everlasting agreement.")
        tBuilder.add("itemTooltip.enchained.bVow2", "Free from time itself.")

    }

    override fun generateTranslations(
        tBuilder: TranslationBuilder
    ) {
        // Item Group
        tBuilder.add("itemGroup.enchained", "Enchained")

        // Item Names
        allModItems.forEach { item ->
            tBuilder.add(item, formatItemName(item))
        }

        // Tooltips
        tBuilder.add("itemTooltip.enchained.primaryUser", "Bound to %s.")
        tBuilder.add("itemTooltip.enchained.secondaryUser", "Signed by %s.")
        tBuilder.add("itemTooltip.enchained.agreementPrimaryUser", "Proposed by %s.")
        tBuilder.add("itemTooltip.agreementSecondaryUser", "Acknowledged by %s.")

        // Messages
        tBuilder.add("message.enchained.binding.same_team", "You cannot sign this.")
        tBuilder.add("message.enchained.binding.three_way_forbidden", "You cannot make a 3 way binding.")



        // Functions for custom tooltips.
        agreement(tBuilder)
        contract(tBuilder)
        charter(tBuilder)
        vow(tBuilder)

    }

}