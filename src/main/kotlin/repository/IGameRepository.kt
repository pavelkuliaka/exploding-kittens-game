package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.GameSession
import java.util.UUID

interface IGameRepository {
    fun addSession(gameSession: GameSession)
    fun getSession(sessionId: UUID): GameSession?
    fun removeSession(sessionId: UUID)
}
