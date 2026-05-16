package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.DeckComposition
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import java.util.UUID
import kotlin.collections.set

fun createSession(
    engine: GameAdminEngine
): UUID? {
    val players = engine.playerRepository.getAllPlayers()

    if (players.size < 2) {
        println("\nNeed 2 players to start a game")
        return null
    }

    println("\nSELECT PLAYERS FOR NEW GAME:")
    players.forEachIndexed { index, player ->
        println("${index + 1}. ${player.name} (${player.id})")
    }

    println("\n(Leave empty to exit)")
    print("Enter first player number: ")
    var input = readLine()?.trim()?.toIntOrNull() ?: return null
    if (input !in 1..players.size) {
        return null
    }
    val player1 = players[input - 1]
    if (player1.isPlaying) {
        println("This player is already playing")
        return null
    }

    print("Enter second player number: ")
    input = readLine()?.trim()?.toIntOrNull() ?: return null
    if (input !in 1..players.size) {
        return null
    }
    val player2 = players[input - 1]
    if (player2.isPlaying) {
        println("This player is already playing")
        return null
    }

    if (player1 == player2) {
        println("You selected already chosen player")
        return null
    }

    println("\nWho will go first?")
    println("1. ${player1.name} (${player1.id})")
    println("2. ${player2.name} (${player2.id})")
    print("Enter player number: ")
    input = readLine()?.trim()?.toIntOrNull() ?: return null

    val sessionId = when (input) {
        1 -> engine.startNewSession(player1.id, player2.id)
        2 -> engine.startNewSession(player2.id, player1.id)
        else -> return null
    }

    val session = engine.gameRepository.getSession(sessionId) ?: return null

    val deckComposition = DeckComposition.CARDS.toMap()

    val availableCards = deckComposition.toMutableMap()

    val hand1 = mutableMapOf(CardType.DEFUSE to 1)
    val hand2 = mutableMapOf(CardType.DEFUSE to 1)
    availableCards[CardType.DEFUSE] = 1

    session.playerHands[player1.id] = hand1
    session.playerHands[player2.id] = hand2

    if (!cardSetupMenu(session, sessionId, player1, player2, hand1, hand2, availableCards, deckComposition, engine)) {
        return null
    }

    val initialPool = availableCards.toMutableMap()

    val drawPile = mutableListOf<CardType>()

    if (!drawPileSetupMenu(session, drawPile, availableCards, initialPool, deckComposition, engine)) {
        engine.gameRepository.removeSession(sessionId)
        return null
    }

    session.drawPile.clear()
    session.drawPile.addAll(drawPile)

    return session.id
}

fun listSessions(gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    val sessions = gameRepository.sessions
    if (sessions.isEmpty()) {
        println("\nNo sessions found")
        return
    }
    println("\nSESSIONS LIST:")
    sessions.entries.forEachIndexed { index, entry ->
        val winnerInfo = if (entry.value.status == GameStatus.FINISHED) {
            val winner = entry.value.winnerId?.let { playerRepository.getPlayer(it)?.name } ?: "none"
            " | Winner: $winner"
        } else ""
        println("${index + 1}. STATUS: ${entry.value.status}${winnerInfo} | ID: ${entry.key} | Participants: ${entry.value.participants.map {
            val player = playerRepository.getPlayer(it)
            "${player?.name} ($it)"
        }}")
    }
}