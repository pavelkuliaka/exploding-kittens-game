package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository

fun resumeGameSession(
    engine: GameAdminEngine,
    gameRepository: JsonGameRepository,
    playerRepository: JsonPlayerRepository
) {
    val activeSessions = gameRepository.sessions.values.filter { it.status == GameStatus.ACTIVE }

    if (activeSessions.isEmpty()) {
        println("\nNo active sessions found")
        return
    }

    println("\nACTIVE SESSIONS:")
    activeSessions.forEachIndexed { index, session ->
        val player1 = playerRepository.getPlayer(session.participants.first())
        val player2 = playerRepository.getPlayer(session.participants.last())
        val turnInfo = session.whoseTurn?.let {
            val currentPlayer = playerRepository.getPlayer(it)
            "Turn: ${currentPlayer?.name}"
        } ?: "Turn: not set"
        println("${index + 1}. ID: ${session.id} | ${player1?.name} vs ${player2?.name} | $turnInfo")
    }

    print("\nChoose session number: ")
    val input = readLine()?.trim()?.toIntOrNull() ?: return
    if (input !in 1..activeSessions.size) {
        return
    }

    val session = activeSessions[input - 1]

    if (session.whoseTurn == null && session.drawPile.isNotEmpty()) {
        println("\nNo current turn is set. Who goes first?")
        session.participants.forEachIndexed { index, playerId ->
            val player = playerRepository.getPlayer(playerId)
            println("${index + 1}. ${player?.name} ($playerId)")
        }
        print("Choose: ")
        val firstInput = readLine()?.trim()?.toIntOrNull()
        if (firstInput != null && firstInput in 1..session.participants.size) {
            session.whoseTurn = session.participants.toList()[firstInput - 1]
        } else {
            return
        }
    }

    playTurns(session, engine, gameRepository, playerRepository)
}
