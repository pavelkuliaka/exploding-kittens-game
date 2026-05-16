package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository

fun forceEndGame(engine: GameAdminEngine, gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    val activeSessions = gameRepository.sessions.values.filter { it.status == GameStatus.ACTIVE }

    println("ACTIVE SESSIONS")
    activeSessions.forEachIndexed { index, session ->
        println("${index + 1}. ID: ${session.id} | Participants: ${session.participants.map {
            val player = playerRepository.getPlayer(it)
            "${player?.name} ($it)"
        }}")
    }

    print("Enter session number: ")
    val input = readLine()?.trim()?.toIntOrNull() ?: return
    if (input !in 1..activeSessions.size) {
        return
    }
    val session = activeSessions[input-1]
    engine.endSession(session.id, null)
}