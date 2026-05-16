package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.repository.DatabaseManager
import com.github.pavelkuliaka.repository.IGameRepository
import com.github.pavelkuliaka.repository.IPlayerRepository
import com.github.pavelkuliaka.repository.SqliteGameRepository
import com.github.pavelkuliaka.repository.SqlitePlayerRepository
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
    lateinit var gameRepository: IGameRepository
    lateinit var playerRepository: IPlayerRepository
    val ruleValidator = RuleValidator()
    val statisticsService get() = StatisticsService(playerRepository)
    val engine get() = GameAdminEngine(gameRepository, playerRepository, ruleValidator, statisticsService)

    var activeSessionId: UUID? = null
    var notificationMessage: String? = null

    val activeSession: GameSession?
        get() = activeSessionId?.let { gameRepository.getSession(it) }
}

object Navigation {
    var container: StackPane = StackPane()

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
        DatabaseManager.init()
        AppDependencies.gameRepository = SqliteGameRepository(DatabaseManager.connection)
        AppDependencies.playerRepository = SqlitePlayerRepository(DatabaseManager.connection)

        val root = StackPane()
        Navigation.container = root

        stage.scene = Scene(root, 900.0, 720.0).apply {
            stylesheets.add(javaClass.getResource("/gui/styles.css")?.toExternalForm() ?: "")
        }
        stage.title = "Exploding Kittens - Game Administration"
        stage.setOnCloseRequest {
            DatabaseManager.close()
            Platform.exit()
        }
        stage.show()

        Navigation.navigateTo(MainMenuView())
    }
}
