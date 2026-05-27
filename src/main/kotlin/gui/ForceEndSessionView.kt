package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.VBox

class ForceEndSessionView : ViewBase() {
    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("FORCE END GAME SESSION").apply { styleClass.add("title") })

        val activeSessions = AppDependencies.gameRepository.getAllSessions()
            .filter { it.status == GameStatus.ACTIVE }

        if (activeSessions.isEmpty()) {
            children.add(Label("No active sessions found"))
        } else {
            val table = TableView<GameSession>().apply {
                prefHeight = 250.0
                columns.addAll(
                    TableColumn<GameSession, String>("ID").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.id.toString().take(8)) }
                    },
                    TableColumn<GameSession, String>("Participants").apply {
                        setCellValueFactory {
                            val names = it.value.participants.joinToString(" vs ") { pid ->
                                AppDependencies.playerRepository.getPlayer(pid)?.name ?: pid.toString()
                            }
                            ReadOnlyObjectWrapper(names)
                        }
                    },
                    TableColumn<GameSession, String>("Turns played").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.turns.size.toString()) }
                    },
                )
                items.setAll(activeSessions)
            }
            children.add(table)

            children.add(Button("Force end selected").apply {
                setOnAction {
                    val session = table.selectionModel.selectedItem ?: return@setOnAction
                    val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
                        title = "Force end session?"
                        headerText = "Are you sure you want to force end session ${session.id.toString().take(8)}?"
                    }.showAndWait()
                    if (confirm.isPresent && confirm.get() == ButtonType.OK) {
                        AppDependencies.engine.endSession(session.id, null)
                        navigateTo(MainMenuView())
                    }
                }
            })
        }

        children.add(Button("Back to menu").apply {
            styleClass.add("back-button")
            setOnAction { navigateTo(MainMenuView()) }
        })
    }
}
