package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.repository.IPlayerRepository
import java.util.UUID

open class TestPlayerRepository : IPlayerRepository {
    val players: MutableMap<UUID, Player> = mutableMapOf()

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
}
