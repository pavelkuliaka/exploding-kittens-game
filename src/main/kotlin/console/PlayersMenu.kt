package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import java.util.UUID

fun addPlayer(playerRepository: JsonPlayerRepository) {
    println("\n(Leave empty to exit)")
    print("Enter player name: ")
    val name = readLine() ?: return
    if (name.isEmpty()) {
        return
    }

    val playerStats = PlayerStats(0, 0, 0, 0, 0)
    val player = Player(UUID.randomUUID(), name, false, playerStats)
    playerRepository.addPlayer(player)
    println("Player added: ${player.name} (ID: ${player.id})")
}

fun listPlayers(playerRepository: JsonPlayerRepository) {
    val players = playerRepository.players
    if (players.isEmpty()) {
        println("\nNo players found")
        return
    }
    println("\nPLAYERS LIST:")
    players.entries.forEachIndexed { index, entry ->
        println("${index + 1}. ${entry.value.name} (${entry.key})")
    }
}

fun showLeaderboard(statisticsService: StatisticsService) {
    println("How many users should I display?")
    println("(Leave empty to exit)")
    print("Enter number: ")
    val input = readLine()?.trim()?.toUIntOrNull() ?: return

    val leaderboard = statisticsService.getLeaderboard(input)
    if (leaderboard.isEmpty()) {
        println("No players found")
        return
    }
    println("\nLEADERBOARD:")
    leaderboard.forEachIndexed { index, player ->
        println("${index + 1}. NAME: ${player.name} | ID: ${player.id} | WINS: ${player.stats.wins} | TOTAL GAMES: ${player.stats.totalGames}")
    }
}
