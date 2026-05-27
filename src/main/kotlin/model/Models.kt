package com.github.pavelkuliaka.model

import java.util.UUID

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

data class Player(
    val id: UUID,
    var name: String,
    var isPlaying: Boolean,
    val stats: PlayerStats
)

data class PlayerStats(
    var totalGames: Int,
    var wins: Int,
    var losses: Int,
    var winRate: Int,
    var defused: Int
)

data class GameStateSnapshot(
    val whoseTurn: UUID?,
    val attackTurnsRemaining: Int,
    val mustDefuse: Boolean,
    val playerHands: Map<UUID, Map<CardType, Int>>,
    val discardPile: Map<CardType, Int>,
    val drawPile: List<CardType>,
    val winnerId: UUID?
)

fun GameSession.snapshot(): GameStateSnapshot {
    return GameStateSnapshot(
        whoseTurn = this.whoseTurn,
        attackTurnsRemaining = this.attackTurnsRemaining,
        mustDefuse = this.mustDefuse,
        playerHands = this.playerHands.mapValues { it.value.toMap() },
        discardPile = this.discardPile.toMap(),
        drawPile = this.drawPile.toList(),
        winnerId = this.winnerId
    )
}

fun GameSession.restoreSnapshot(snapshot: GameStateSnapshot) {
    this.whoseTurn = snapshot.whoseTurn
    this.attackTurnsRemaining = snapshot.attackTurnsRemaining
    this.mustDefuse = snapshot.mustDefuse
    this.winnerId = snapshot.winnerId
    this.playerHands.clear()
    snapshot.playerHands.forEach { (id, hand) ->
        this.playerHands[id] = hand.toMutableMap()
    }
    this.discardPile.clear()
    snapshot.discardPile.forEach { (type, count) ->
        this.discardPile[type] = count
    }
    this.drawPile.clear()
    this.drawPile.addAll(snapshot.drawPile)
}

data class GameSession(
    val id: UUID,
    val participants: Set<UUID>,
    val turns: MutableList<Turn>,
    val discardPile: MutableMap<CardType, Int>,
    val drawPile: MutableList<CardType> = mutableListOf(),
    var status: GameStatus,
    var whoseTurn: UUID?,
    val playerHands: MutableMap<UUID, MutableMap<CardType, Int>> = mutableMapOf(),
    var initialState: GameStateSnapshot? = null,
    var attackTurnsRemaining: Int = 0,
    var mustDefuse: Boolean = false,
    var winnerId: UUID? = null
)

sealed class Turn {
    abstract val playerId: UUID
    data class DrawCard(
        override val playerId: UUID,
        val card: CardType
    ) : Turn()

    data class Defuse(
        override val playerId: UUID,
        val insertPosition: Int
    ) : Turn()
    data class Nope(
        override val playerId: UUID,
        val targetTurnIndex: Int
    ) : Turn()
    data class Attack(
        override val playerId: UUID
    ) : Turn()
    data class Skip(
        override val playerId: UUID
    ) : Turn()
    data class SeeTheFuture(
        override val playerId: UUID
    ) : Turn()
    data class Shuffle(
        override val playerId: UUID,
        val newDrawPile: List<CardType>
    ) : Turn()
    data class Favor(
        override val playerId: UUID,
        val takenCard: CardType
    ) : Turn()
    data class Pass(
        override val playerId: UUID
    ) : Turn()
    data class PlayDouble(
        override val playerId: UUID,
        val card: CardType,
        val stolenCard: CardType
    ) : Turn()
    data class PlayTriple(
        override val playerId: UUID,
        val card: CardType
    ) : Turn()
}
