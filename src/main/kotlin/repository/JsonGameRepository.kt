package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.GameSession
import java.util.UUID
import com.google.gson.Gson
import com.fatboyindustrial.gsonjavatime.Converters
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

class JsonGameRepository(val filePath: String) : IGameRepository {
    var sessions: MutableMap<UUID, GameSession> = mutableMapOf()
    private val gson: Gson = createGson()

    private fun createGson(): Gson {
        return Converters.registerLocalDateTime(GsonBuilder())
            .setPrettyPrinting()
            .enableComplexMapKeySerialization()
            .create()
    }

    override fun addSession(gameSession: GameSession) {
        sessions[gameSession.id] = gameSession
    }
    override fun getSession(sessionId: UUID): GameSession? {
        return sessions[sessionId]
    }
    override fun removeSession(sessionId: UUID) {
        sessions.remove(sessionId)
    }

    @Suppress("UNCHECKED_CAST")
    fun loadSessions() : Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            sessions = mutableMapOf()
            return false
        }

        try {
            val json = file.readText()
            val type = object : TypeToken<MutableMap<UUID, GameSession>>() {}.type
            val loaded = gson.fromJson<MutableMap<UUID, GameSession>>(json, type)
            sessions = loaded?.toMutableMap() ?: mutableMapOf()
            return true
        } catch (_: Exception) {
            sessions = mutableMapOf()
            return false
        }
    }
    fun saveSessions() : Boolean {
        try {
            val json = gson.toJson(sessions)
            File(filePath).writeText(json)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
