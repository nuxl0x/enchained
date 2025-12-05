package nux.enchained.chat

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import nux.enchained.teams.TeamManager

object TeamChatFormatter {

    fun register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, params ->
            val server = sender.server ?: return@register true

            val teamOpt = TeamManager.get(server).getTeamOfPlayer(sender.uuid)
            if (teamOpt.isEmpty) {
                // Not in a team: use vanilla formatting
                return@register true
            }

            val team = teamOpt.get()
            val teamName = team.name
            val teamColorInt = team.color

            val teamColor = TextColor.fromRgb(teamColorInt)

            val teamPrefix = Text.literal("[$teamName] ").styled { it.withColor(teamColor) }

            val playerPrefix = Text.literal("<${sender.entityName}> ")

            val rawMessage = message.content.string
            val contentText = Text.literal(rawMessage)

            val full = Text.empty()
                .append(teamPrefix)
                .append(playerPrefix)
                .append(contentText)

            // Broadcast formatted line to all players (as a normal chat message)
            server.playerManager.broadcast(full, false)

            // Return false to cancel vanilla chat handling, so it doesn't send a duplicate
            false
        }
    }
}