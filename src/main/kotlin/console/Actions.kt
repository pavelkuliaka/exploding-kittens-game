package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.model.ActionType
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import java.util.UUID

data class ActionOption(
    val type: ActionType,
    val label: String
)

fun buildActions(
    session: GameSession,
    currentPlayerId: UUID,
    opponentId: UUID,
    playerRepository: JsonPlayerRepository
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
