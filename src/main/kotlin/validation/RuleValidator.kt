package com.github.pavelkuliaka.validation

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.DeckComposition
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.model.ValidationResult
import java.util.UUID

interface IRuleValidator {
    fun validateCardDistribution(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ): ValidationResult
    fun validateDrawPile(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ): ValidationResult
    fun validateTurn(
        gameSession: GameSession,
        nextTurn: Turn
    ): Boolean
}

class RuleValidator : IRuleValidator {
    override fun validateCardDistribution(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ): ValidationResult {
        val errors = mutableListOf<String>()

        validatePlayerHands(session, errors)

        val totalInPlay = session.playerHands.values.sumOf { it.values.sum() } +
            session.drawPile.size + availableCards.values.sum()
        if (totalInPlay != DeckComposition.TOTAL_CARDS) {
            errors += "Total cards in play ($totalInPlay) != expected (${DeckComposition.TOTAL_CARDS})"
        }

        val allCardsInPlay = collectAllCardsInPlay(session, availableCards)
        validateCardLimits(allCardsInPlay, deckComposition, errors)

        return ValidationResult(errors.isEmpty(), errors)
    }

    override fun validateDrawPile(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ): ValidationResult {
        val errors = mutableListOf<String>()

        val remainingCards = availableCards.filterValues { it > 0 }
        if (remainingCards.isNotEmpty()) {
            errors += "Pool is not empty (${remainingCards.values.sum()} cards remaining)"
        }

        validatePlayerHands(session, errors)

        val totalInPlay = session.playerHands.values.sumOf { it.values.sum() } + session.drawPile.size
        if (totalInPlay != DeckComposition.TOTAL_CARDS) {
            errors += "Total cards in play ($totalInPlay) != expected (${DeckComposition.TOTAL_CARDS})"
        }

        val allCardsInPlay = collectAllCardsInPlay(session)
        validateCardLimits(allCardsInPlay, deckComposition, errors)

        return ValidationResult(errors.isEmpty(), errors)
    }

    override fun validateTurn(gameSession: GameSession, nextTurn: Turn): Boolean {
        if (gameSession.status != GameStatus.ACTIVE) return false
        if (nextTurn.playerId !in gameSession.participants) return false
        val hand = gameSession.playerHands[nextTurn.playerId] ?: return false

        return when (nextTurn) {
            is Turn.Nope -> validateNopeTurn(gameSession, nextTurn, hand)
            is Turn.Defuse -> validateDefuseTurn(gameSession, nextTurn, hand)
            is Turn.DrawCard -> validateDrawCardTurn(gameSession, nextTurn)
            is Turn.Attack -> validateAttackTurn(gameSession, nextTurn, hand)
            is Turn.Skip -> validateSkipTurn(gameSession, nextTurn, hand)
            is Turn.SeeTheFuture -> validateSeeTheFutureTurn(gameSession, nextTurn, hand)
            is Turn.Shuffle -> validateShuffleTurn(gameSession, nextTurn, hand)
            is Turn.Favor -> validateFavorTurn(gameSession, nextTurn, hand)
            is Turn.PlayDouble -> validatePlayDoubleTurn(gameSession, nextTurn, hand)
            is Turn.PlayTriple -> validatePlayTripleTurn(gameSession, nextTurn, hand)
            is Turn.Pass -> false
        }
    }

    private fun validateNopeTurn(gameSession: GameSession, turn: Turn.Nope, hand: Map<CardType, Int>): Boolean {
        if (turn.targetTurnIndex < 0 || turn.targetTurnIndex >= gameSession.turns.size) return false
        val targetTurn = gameSession.turns[turn.targetTurnIndex]
        if (targetTurn is Turn.Defuse) return false
        if (targetTurn is Turn.DrawCard) return false
        return (hand[CardType.NOPE] ?: 0) > 0
    }

    private fun validateDefuseTurn(gameSession: GameSession, turn: Turn.Defuse, hand: Map<CardType, Int>): Boolean {
        if (!gameSession.mustDefuse) return false
        if (turn.playerId != gameSession.whoseTurn) return false
        if ((hand[CardType.DEFUSE] ?: 0) < 1) return false
        if (turn.insertPosition < 0) return false
        return true
    }

    private fun validateDrawCardTurn(gameSession: GameSession, turn: Turn.DrawCard): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        if (gameSession.drawPile.isEmpty()) return false
        if (gameSession.drawPile.first() != turn.card) return false
        return true
    }

    private fun validateAttackTurn(gameSession: GameSession, turn: Turn.Attack, hand: Map<CardType, Int>): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        return (hand[CardType.ATTACK] ?: 0) >= 1
    }

    private fun validateSkipTurn(gameSession: GameSession, turn: Turn.Skip, hand: Map<CardType, Int>): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        return (hand[CardType.SKIP] ?: 0) >= 1
    }

    private fun validateSeeTheFutureTurn(
        gameSession: GameSession,
        turn: Turn.SeeTheFuture,
        hand: Map<CardType, Int>
    ): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        return (hand[CardType.SEE_THE_FUTURE] ?: 0) >= 1
    }

    private fun validateShuffleTurn(gameSession: GameSession, turn: Turn.Shuffle, hand: Map<CardType, Int>): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        if ((hand[CardType.SHUFFLE] ?: 0) < 1) return false
        if (turn.newDrawPile.size != gameSession.drawPile.size) return false
        if (turn.newDrawPile.groupBy { it }.mapValues { it.value.size } !=
            gameSession.drawPile.groupBy { it }.mapValues { it.value.size }) return false
        return true
    }

    private fun validateFavorTurn(gameSession: GameSession, turn: Turn.Favor, hand: Map<CardType, Int>): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        if ((hand[CardType.FAVOR] ?: 0) < 1) return false
        val opponent = getOpponent(gameSession, turn.playerId)
        return opponent != null && hasCard(gameSession, opponent, turn.takenCard)
    }

    private fun validatePlayDoubleTurn(
        gameSession: GameSession,
        turn: Turn.PlayDouble,
        hand: Map<CardType, Int>
    ): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        if (!isCatCard(turn.card)) return false
        if ((hand[turn.card] ?: 0) < 2) return false
        val opponent = getOpponent(gameSession, turn.playerId)
        return opponent != null && hasCard(gameSession, opponent, turn.stolenCard)
    }

    private fun validatePlayTripleTurn(
        gameSession: GameSession,
        turn: Turn.PlayTriple,
        hand: Map<CardType, Int>
    ): Boolean {
        if (turn.playerId != gameSession.whoseTurn) return false
        if (gameSession.mustDefuse) return false
        if (!isCatCard(turn.card)) return false
        return (hand[turn.card] ?: 0) >= 3
    }

    private fun validatePlayerHands(session: GameSession, errors: MutableList<String>) {
        for ((playerId, hand) in session.playerHands) {
            val handSize = hand.values.sum()
            if (handSize != 8) {
                errors += "Player $playerId has $handSize/8 cards"
            }
            val defuseCount = hand[CardType.DEFUSE] ?: 0
            if (defuseCount < 1) {
                errors += "Player $playerId has no DEFUSE card"
            }
        }
    }

    private fun collectAllCardsInPlay(
        session: GameSession,
        availableCards: Map<CardType, Int> = emptyMap()
    ): Map<CardType, Int> {
        val result = mutableMapOf<CardType, Int>()
        session.playerHands.values.forEach { hand ->
            hand.forEach { (type, count) ->
                result[type] = (result[type] ?: 0) + count
            }
        }
        session.drawPile.groupingBy { it }.eachCount().forEach { (type, count) ->
            result[type] = (result[type] ?: 0) + count
        }
        availableCards.forEach { (type, count) ->
            result[type] = (result[type] ?: 0) + count
        }
        return result
    }

    private fun validateCardLimits(
        allCardsInPlay: Map<CardType, Int>,
        deckComposition: Map<CardType, Int>,
        errors: MutableList<String>
    ) {
        for ((type, countInPlay) in allCardsInPlay) {
            val maxAllowed = deckComposition[type] ?: 0
            if (countInPlay > maxAllowed) {
                errors += "Type $type has $countInPlay cards (max $maxAllowed)"
            }
        }
    }

    private fun getOpponent(session: GameSession, playerId: UUID): UUID? {
        return session.participants.firstOrNull { it != playerId }
    }

    private fun hasCard(session: GameSession, playerId: UUID, card: CardType): Boolean {
        val hand = session.playerHands[playerId] ?: return false
        return (hand[card] ?: 0) > 0
    }

    private fun isCatCard(card: CardType): Boolean {
        return card in setOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
    }
}
