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

        val totalInPlay = session.playerHands.values.sumOf { it.values.sum() } + session.drawPile.size + availableCards.values.sum()
        if (totalInPlay != DeckComposition.TOTAL_CARDS) {
            errors += "Total cards in play ($totalInPlay) != expected (${DeckComposition.TOTAL_CARDS})"
        }

        val allCardsInPlay = mutableMapOf<CardType, Int>()
        session.playerHands.values.forEach { hand ->
            hand.forEach { (type, count) ->
                allCardsInPlay[type] = (allCardsInPlay[type] ?: 0) + count
            }
        }
        session.drawPile.groupingBy { it }.eachCount().forEach { (type, count) ->
            allCardsInPlay[type] = (allCardsInPlay[type] ?: 0) + count
        }
        availableCards.forEach { (type, count) ->
            allCardsInPlay[type] = (allCardsInPlay[type] ?: 0) + count
        }

        for ((type, countInPlay) in allCardsInPlay) {
            val maxAllowed = deckComposition[type] ?: 0
            if (countInPlay > maxAllowed) {
                errors += "Type $type has $countInPlay cards (max $maxAllowed)"
            }
        }

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

        val totalInPlay = session.playerHands.values.sumOf { it.values.sum() } + session.drawPile.size
        if (totalInPlay != DeckComposition.TOTAL_CARDS) {
            errors += "Total cards in play ($totalInPlay) != expected (${DeckComposition.TOTAL_CARDS})"
        }

        val allCardsInPlay = mutableMapOf<CardType, Int>()
        session.playerHands.values.forEach { hand ->
            hand.forEach { (type, count) ->
                allCardsInPlay[type] = (allCardsInPlay[type] ?: 0) + count
            }
        }
        session.drawPile.groupingBy { it }.eachCount().forEach { (type, count) ->
            allCardsInPlay[type] = (allCardsInPlay[type] ?: 0) + count
        }

        for ((type, countInPlay) in allCardsInPlay) {
            val maxAllowed = deckComposition[type] ?: 0
            if (countInPlay > maxAllowed) {
                errors += "Type $type has $countInPlay cards (max $maxAllowed)"
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    override fun validateTurn(gameSession: GameSession, nextTurn: Turn): Boolean {
        if (gameSession.status != GameStatus.ACTIVE) return false
        if (nextTurn.playerId !in gameSession.participants) return false
        if (nextTurn.playerId !in gameSession.playerHands) return false

        val hand = gameSession.playerHands[nextTurn.playerId] ?: return false

        return when (nextTurn) {
            is Turn.Nope -> {
                if (nextTurn.targetTurnIndex < 0 || nextTurn.targetTurnIndex >= gameSession.turns.size) return false
                val targetTurn = gameSession.turns[nextTurn.targetTurnIndex]
                if (targetTurn is Turn.Defuse) return false
                if (targetTurn is Turn.DrawCard) return false
                (hand[CardType.NOPE] ?: 0) > 0
            }

            is Turn.Defuse -> {
                if (!gameSession.mustDefuse) return false
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if ((hand[CardType.DEFUSE] ?: 0) < 1) return false
                if (nextTurn.insertPosition < 0) return false
                true
            }

            is Turn.DrawCard -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if (gameSession.drawPile.isEmpty()) return false
                if (gameSession.drawPile.first() != nextTurn.card) return false
                true
            }

            is Turn.Attack -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                (hand[CardType.ATTACK] ?: 0) >= 1
            }

            is Turn.Skip -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                (hand[CardType.SKIP] ?: 0) >= 1
            }

            is Turn.SeeTheFuture -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                (hand[CardType.SEE_THE_FUTURE] ?: 0) >= 1
            }

            is Turn.Shuffle -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if ((hand[CardType.SHUFFLE] ?: 0) < 1) return false
                if (nextTurn.newDrawPile.size != gameSession.drawPile.size) return false
                if (nextTurn.newDrawPile.groupBy { it }.mapValues { it.value.size } != gameSession.drawPile.groupBy { it }.mapValues { it.value.size }) return false
                true
            }

            is Turn.Favor -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if ((hand[CardType.FAVOR] ?: 0) < 1) return false
                val opponent = getOpponent(gameSession, nextTurn.playerId)
                opponent != null && hasCard(gameSession, opponent, nextTurn.takenCard)
            }

            is Turn.PlayDouble -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if (!isCatCard(nextTurn.card)) return false
                if ((hand[nextTurn.card] ?: 0) < 2) return false
                val opponent = getOpponent(gameSession, nextTurn.playerId)
                opponent != null && hasCard(gameSession, opponent, nextTurn.stolenCard)
            }

            is Turn.PlayTriple -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if (!isCatCard(nextTurn.card)) return false
                (hand[nextTurn.card] ?: 0) >= 3
            }

            is Turn.Pass -> false
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
