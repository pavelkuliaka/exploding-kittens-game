package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.util.UUID

class AddPlayerView : ViewBase() {
    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("ADD PLAYER").apply { styleClass.add("title") })

        val nameField = TextField().apply {
            promptText = "Enter player name"
            prefWidth = 300.0
        }
        val errorLabel = Label("").apply { styleClass.add("validation-error") }

        children.addAll(
            nameField,
            HBox(8.0).apply {
                children.addAll(
                    Button("Add").apply {
                        setOnAction {
                            val name = nameField.text.trim()
                            if (name.isEmpty()) {
                                errorLabel.text = "Name cannot be empty"
                                return@setOnAction
                            }
                            val stats = PlayerStats(0, 0, 0, 0, 0)
                            val player = Player(UUID.randomUUID(), name, false, stats)
                            AppDependencies.playerRepository.addPlayer(player)
                            AppDependencies.notificationMessage = "Player added: ${player.name} (${player.id})"
                            navigateTo(MainMenuView())
                        }
                    },
                    Button("Back to menu").apply {
                        styleClass.add("back-button")
                        setOnAction { navigateTo(MainMenuView()) }
                    }
                )
            },
            errorLabel
        )
    }
}
