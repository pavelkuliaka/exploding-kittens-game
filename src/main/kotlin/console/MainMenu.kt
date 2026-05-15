package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import kotlin.system.exitProcess

fun mainMenu(
    gameRepository: JsonGameRepository,
    playerRepository: JsonPlayerRepository,
    statisticsService: StatisticsService,
    engine: GameAdminEngine) {
    while (true) {
        println("\nMAIN MENU:")
        println("1. List all players")
        println("2. Show leaderboard")
        println("3. Add new player")
        println("4. List sessions")
        println("5. Create new game session")
        println("6. Resume active game session")
        println("7. Force end game session")

        println("0. Exit")
        print("Choose option: ")

        val input = readLine() ?: break
        val option = input.toIntOrNull()
        clear()

        when (option) {
            1 -> listPlayers(playerRepository)
            2 -> showLeaderboard(statisticsService)
            3 -> addPlayer(playerRepository)
            4 -> listSessions(gameRepository, playerRepository)
            5 -> createSession(engine)
            6 -> resumeGameSession(engine, gameRepository, playerRepository)
            7 -> forceEndGame(engine, gameRepository, playerRepository)
            0 -> {
                exit(gameRepository, playerRepository)
                break
            }
            else -> println("Invalid option. Please try again")
        }
    }
}

fun exit(gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    if (!gameRepository.saveSessions() || !playerRepository.savePlayers()) {
        println("Failed to save data to JSON")
        print("Do you want to leave without saving data? (Y/N): ")
        val answer = readLine() ?: ""
        if (answer.equals("y", ignoreCase = true)) {
            exitProcess(1)
        } else {
            return
        }
    }
    println("Data has been saved to JSON files")
    return
}