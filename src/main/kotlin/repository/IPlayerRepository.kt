package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.Player
import java.util.UUID

interface IPlayerRepository {
    fun addPlayer(player: Player)
    fun getPlayer(playerId: UUID): Player?
    fun removePlayer(playerId: UUID)
    fun getAllPlayers(): List<Player>
}