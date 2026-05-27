package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.repository.IGameRepository
import java.util.UUID

open class TestGameRepository : IGameRepository {
    val sessions: MutableMap<UUID, GameSession> = mutableMapOf()

    override fun addSession(gameSession: GameSession) {
        sessions[gameSession.id] = gameSession
    }

    override fun getSession(sessionId: UUID): GameSession? {
        return sessions[sessionId]
    }

    override fun removeSession(sessionId: UUID) {
        sessions.remove(sessionId)
    }

    override fun getAllSessions(): List<GameSession> {
        return sessions.values.toList()
    }
}
