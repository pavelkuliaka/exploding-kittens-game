package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator

fun main() {
    val gameRepository = JsonGameRepository("games.json")
    if (!gameRepository.loadSessions()) {
        println("Information about the game sessions has not been uploaded")
    }

    val playerRepository = JsonPlayerRepository("players.json")
    if (!playerRepository.loadPlayers()) {
        println("Player information has not been uploaded")
    }

    val ruleValidator = RuleValidator()
    val statisticsService = StatisticsService(playerRepository)
    val engine = GameAdminEngine(gameRepository, playerRepository, ruleValidator, statisticsService)

    clear()

    println("\n" + "=".repeat(60))
    println("EXPLODING KITTENS - Game Administration Console")
    println("=".repeat(60))

    mainMenu(gameRepository, playerRepository, statisticsService, engine)
}