package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.Player
import java.util.UUID

import com.google.gson.Gson
import com.fatboyindustrial.gsonjavatime.Converters
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File


class JsonPlayerRepository(val filePath: String) : IPlayerRepository {
    var players: MutableMap<UUID, Player> = mutableMapOf()
    private val gson: Gson = createGson()

    private fun createGson(): Gson {
        return Converters.registerLocalDateTime(GsonBuilder())
            .setPrettyPrinting()
            .enableComplexMapKeySerialization()
            .create()
    }

    override fun addPlayer(player: Player) {
        players[player.id] = player
    }

    override fun getPlayer(playerId: UUID): Player? {
        return players[playerId]
    }

    override fun removePlayer(playerId: UUID) {
        players.remove(playerId)
    }

    override fun getAllPlayers(): List<Player> {
        return players.values.toList()
    }

    fun loadPlayers() : Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            players = mutableMapOf()
            return true
        }

        try {
            val json = file.readText()
            val type = object : TypeToken<MutableMap<UUID, Player>>() {}.type
            val loaded = gson.fromJson<MutableMap<UUID, Player>>(json, type)
            players = loaded?.toMutableMap() ?: mutableMapOf()
            return true
        } catch (_: Exception) {
            players = mutableMapOf()
            return false
        }
    }

    fun savePlayers() : Boolean {
        try {
            val json = gson.toJson(players)
            File(filePath).writeText(json)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
