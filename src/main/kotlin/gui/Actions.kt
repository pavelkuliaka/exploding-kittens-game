package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.ActionType
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.repository.IPlayerRepository
import java.util.UUID

data class ActionOption(
    val type: ActionType,
    val label: String
)

private val catCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)

private val actionCardMap = mapOf(
    CardType.ATTACK to ActionType.ATTACK,
    CardType.SKIP to ActionType.SKIP,
    CardType.SEE_THE_FUTURE to ActionType.SEE_THE_FUTURE,
    CardType.SHUFFLE to ActionType.SHUFFLE,
    CardType.FAVOR to ActionType.FAVOR,
)

private val actionLabels = mapOf(
    ActionType.ATTACK to "Play Attack",
    ActionType.SKIP to "Play Skip",
    ActionType.SEE_THE_FUTURE to "Play See the Future",
    ActionType.SHUFFLE to "Play Shuffle",
    ActionType.FAVOR to "Play Favor",
)

fun buildActions(
    session: GameSession,
    currentPlayerId: UUID,
    opponentId: UUID,
    playerRepository: IPlayerRepository
): List<ActionOption> {
    if (session.mustDefuse) {
        return listOf(defuseAction(currentPlayerId))
    }

    val currentHand = session.playerHands[currentPlayerId] ?: mutableMapOf()
    val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()

    return buildDrawAction(session) +
            buildActionCardActions(currentHand, opponentHand) +
            buildCatCardActions(currentHand, opponentHand) +
            buildNopeActions(session, playerRepository)
}

private fun defuseAction(playerId: UUID) =
    ActionOption(ActionType.DEFUSE, "Play Defuse (${playerId.short()})")

private fun buildDrawAction(session: GameSession): List<ActionOption> {
    val topCard = session.drawPile.firstOrNull() ?: return emptyList()
    return listOf(ActionOption(ActionType.DRAW_CARD, "Draw card: $topCard"))
}

private fun buildActionCardActions(
    currentHand: Map<CardType, Int>,
    opponentHand: Map<CardType, Int>
): List<ActionOption> {
    val actions = mutableListOf<ActionOption>()
    for ((card, action) in actionCardMap) {
        if ((currentHand[card] ?: 0) == 0) continue
        if (card == CardType.FAVOR && opponentHand.values.sum() == 0) continue
        actions += ActionOption(action, actionLabels[action]!!)
    }
    return actions
}

private fun buildCatCardActions(
    currentHand: Map<CardType, Int>,
    opponentHand: Map<CardType, Int>
): List<ActionOption> {
    val actions = mutableListOf<ActionOption>()
    for (cat in catCards) {
        val count = currentHand[cat] ?: 0
        if (count >= 2 && opponentHand.values.sum() > 0) {
            actions += ActionOption(ActionType.PLAY_DOUBLE, "Play Double $cat")
        }
        if (count >= 3) {
            actions += ActionOption(ActionType.PLAY_TRIPLE, "Play Triple $cat")
        }
    }
    return actions
}

private fun buildNopeActions(
    session: GameSession,
    playerRepository: IPlayerRepository
): List<ActionOption> {
    val actions = mutableListOf<ActionOption>()
    for (playerId in session.participants) {
        val hand = session.playerHands[playerId] ?: mutableMapOf()
        if ((hand[CardType.NOPE] ?: 0) > 0) {
            val player = playerRepository.getPlayer(playerId)
            actions += ActionOption(ActionType.NOPE, "Play Nope (${player?.name})")
        }
    }
    return actions
}

fun turnDescription(turn: Turn): String {
    return when (turn) {
        is Turn.DrawCard -> "Drew ${turn.card}"
        is Turn.Defuse -> "Defused (pos ${turn.insertPosition})"
        is Turn.Nope -> "Nope -> turn #${turn.targetTurnIndex}"
        is Turn.Attack -> "Played Attack"
        is Turn.Skip -> "Played Skip"
        is Turn.SeeTheFuture -> "Played See the Future"
        is Turn.Shuffle -> "Played Shuffle"
        is Turn.Favor -> "Played Favor (took ${turn.takenCard})"
        is Turn.Pass -> "Pass"
        is Turn.PlayDouble -> "Double ${turn.card} -> stole ${turn.stolenCard}"
        is Turn.PlayTriple -> "Triple ${turn.card}"
    }
}

fun ActionOption.playerIdFromLabel(session: GameSession): UUID? {
    val match = Regex("\\((.+)\\)").find(this.label)
    return match?.groupValues?.get(1)?.let { str ->
        session.participants.find { it.short() == str || it.toString() == str }
    }
}

fun UUID.short(): String = this.toString().take(8)
