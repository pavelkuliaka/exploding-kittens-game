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

fun buildActions(
    session: GameSession,
    currentPlayerId: UUID,
    opponentId: UUID,
    playerRepository: IPlayerRepository
): List<ActionOption> {
    val actions = mutableListOf<ActionOption>()
    val currentHand = session.playerHands[currentPlayerId] ?: mutableMapOf()
    val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()

    if (session.mustDefuse) {
        actions += ActionOption(ActionType.DEFUSE, "Play Defuse (${currentPlayerId.short()})")
        return actions
    }

    val topCard = session.drawPile.firstOrNull()
    if (topCard != null) {
        actions += ActionOption(ActionType.DRAW_CARD, "Draw card: $topCard")
    }

    if ((currentHand[CardType.ATTACK] ?: 0) > 0) {
        actions += ActionOption(ActionType.ATTACK, "Play Attack")
    }

    if ((currentHand[CardType.SKIP] ?: 0) > 0) {
        actions += ActionOption(ActionType.SKIP, "Play Skip")
    }

    if ((currentHand[CardType.SEE_THE_FUTURE] ?: 0) > 0) {
        actions += ActionOption(ActionType.SEE_THE_FUTURE, "Play See the Future")
    }

    if ((currentHand[CardType.SHUFFLE] ?: 0) > 0) {
        actions += ActionOption(ActionType.SHUFFLE, "Play Shuffle")
    }

    if ((currentHand[CardType.FAVOR] ?: 0) > 0 && opponentHand.values.sum() > 0) {
        actions += ActionOption(ActionType.FAVOR, "Play Favor")
    }

    val catCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
        .filter { (currentHand[it] ?: 0) >= 2 }
    for (catCard in catCards) {
        if (opponentHand.values.sum() > 0) {
            actions += ActionOption(ActionType.PLAY_DOUBLE, "Play Double $catCard")
        }
    }

    val tripleCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
        .filter { (currentHand[it] ?: 0) >= 3 }
    for (tripleCard in tripleCards) {
        actions += ActionOption(ActionType.PLAY_TRIPLE, "Play Triple $tripleCard")
    }

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
