package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import java.util.UUID

object AppDependencies {
    val gameRepository = JsonGameRepository("games.json")
    val playerRepository = JsonPlayerRepository("players.json")
    val ruleValidator = RuleValidator()
    val statisticsService = StatisticsService(playerRepository)
    val engine = GameAdminEngine(gameRepository, playerRepository, ruleValidator, statisticsService)

    var activeSessionId: UUID? = null
    var notificationMessage: String? = null

    val activeSession: GameSession?
        get() = activeSessionId?.let { gameRepository.getSession(it) }
}

object Navigation {
    lateinit var container: StackPane

    fun navigateTo(view: ViewBase) {
        container.children.setAll(view.root)
        view.onShown()
    }
}

abstract class ViewBase {
    abstract val root: Parent
    open fun onShown() {}
    protected fun navigateTo(view: ViewBase) = Navigation.navigateTo(view)
}

fun main() {
    Application.launch(ExplodingKittensApp::class.java)
}

class ExplodingKittensApp : Application() {
    override fun start(stage: Stage) {
        AppDependencies.gameRepository.loadSessions()
        AppDependencies.playerRepository.loadPlayers()

        val root = StackPane()
        Navigation.container = root

        stage.scene = Scene(root, 900.0, 720.0).apply {
            stylesheets.add(javaClass.getResource("/gui/styles.css")?.toExternalForm() ?: "")
        }
        stage.title = "Exploding Kittens - Game Administration"
        stage.setOnCloseRequest {
            AppDependencies.gameRepository.saveSessions()
            AppDependencies.playerRepository.savePlayers()
            Platform.exit()
        }
        stage.show()

        Navigation.navigateTo(MainMenuView())
    }
}
