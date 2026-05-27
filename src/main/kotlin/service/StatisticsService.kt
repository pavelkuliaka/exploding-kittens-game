package com.github.pavelkuliaka.service

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.repository.IPlayerRepository

interface IStatisticsService {
    val playerRepository: IPlayerRepository
    fun getLeaderboard(number: UInt): List<Player>
}

class StatisticsService(
    override val playerRepository: IPlayerRepository
) : IStatisticsService {
    override fun getLeaderboard(number: UInt): List<Player> {
        val players = playerRepository.getAllPlayers()
        return players.sortedWith(
            compareBy<Player> { it.stats.wins }.thenByDescending { it.stats.totalGames }
                .thenBy { it.name }
        ).take(number.toInt())
    }
}