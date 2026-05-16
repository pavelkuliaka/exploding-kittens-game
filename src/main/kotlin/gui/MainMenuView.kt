package com.github.pavelkuliaka.gui

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox

class MainMenuView : ViewBase() {
    private val notificationLabel = Label().apply {
        style = "-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-padding: 0 0 0 20;"
        isVisible = false
        isManaged = false
    }

    override val root: Parent = VBox(10.0).apply {
        padding = Insets(25.0)

        children.addAll(
            Label("EXPLODING KITTENS\nGame Administration Console").apply { styleClass.add("title") },
            notificationLabel,
            VBox(8.0).apply {
                padding = Insets(15.0, 0.0, 15.0, 20.0)
            }.also { box ->
                box.children.addAll(
                    navBtn("1. List all players") { navigateTo(PlayersListView()) },
                    navBtn("2. Show leaderboard") { navigateTo(LeaderboardView()) },
                    navBtn("3. Add new player") { navigateTo(AddPlayerView()) },
                    navBtn("4. List sessions") { navigateTo(SessionsListView()) },
                    navBtn("5. Create new game session") { navigateTo(CreateSessionView()) },
                    navBtn("6. Resume active game session") { navigateTo(ResumeSessionView()) },
                    navBtn("7. Force end game session") { navigateTo(ForceEndSessionView()) },
                )
            },
            Button("0. Exit").apply {
                styleClass.add("nav-button")
                setOnAction {
                    AppDependencies.gameRepository.saveSessions()
                    AppDependencies.playerRepository.savePlayers()
                    Platform.exit()
                }
            }.also { VBox.setMargin(it, Insets(0.0, 0.0, 0.0, 20.0)) }
        )
    }

    override fun onShown() {
        val msg = AppDependencies.notificationMessage
        if (msg != null) {
            notificationLabel.text = msg
            notificationLabel.isVisible = true
            notificationLabel.isManaged = true
            AppDependencies.notificationMessage = null
        } else {
            notificationLabel.isVisible = false
            notificationLabel.isManaged = false
        }
    }

    private fun navBtn(text: String, action: () -> Unit) =
        Button(text).apply {
            styleClass.add("nav-button")
            setOnAction { action() }
        }
}
