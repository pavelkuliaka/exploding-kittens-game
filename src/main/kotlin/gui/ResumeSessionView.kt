package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage

class ResumeSessionView : ViewBase() {
    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("RESUME ACTIVE SESSION").apply { styleClass.add("title") })

        val activeSessions = AppDependencies.gameRepository.sessions.values
            .filter { it.status == GameStatus.ACTIVE }
            .toList()

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
                    TableColumn<GameSession, String>("Turn").apply {
                        setCellValueFactory {
                            val turnName = it.value.whoseTurn?.let { wid ->
                                AppDependencies.playerRepository.getPlayer(wid)?.name ?: ""
                            } ?: "not set"
                            ReadOnlyObjectWrapper(turnName)
                        }
                    },
                    TableColumn<GameSession, String>("Cards in pile").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.drawPile.size.toString()) }
                    },
                )
                items.setAll(activeSessions)
            }
            children.add(table)

            children.add(Button("Resume selected").apply {
                setOnAction {
                    val session = table.selectionModel.selectedItem ?: return@setOnAction
                    if (session.whoseTurn == null && session.drawPile.isNotEmpty()) {
                        showFirstPlayerDialog(session)
                    } else {
                        AppDependencies.activeSessionId = session.id
                        navigateTo(GamePlayView())
                    }
                }
            })
        }

        children.add(Button("Back to menu").apply {
            styleClass.add("back-button")
            setOnAction { navigateTo(MainMenuView()) }
        })
    }

    private fun showFirstPlayerDialog(session: GameSession) {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.title = "Select First Player"
        val players = session.participants.toList()
        val selected = arrayOf(players.firstOrNull())
        val tg = ToggleGroup()

        dialog.scene = Scene(VBox(8.0).apply {
            padding = Insets(15.0)
            children.add(Label("No current turn is set. Who goes first?").apply {
                styleClass.add("section-title")
            })
            players.forEach { pid ->
                val name = AppDependencies.playerRepository.getPlayer(pid)?.name ?: pid.toString()
                RadioButton(name).apply {
                    toggleGroup = tg
                    if (pid == selected[0]) isSelected = true
                    setOnAction { selected[0] = pid }
                }.also { children.add(it) }
            }
            children.add(Button("Confirm").apply {
                setOnAction {
                    session.whoseTurn = selected[0]
                    AppDependencies.activeSessionId = session.id
                    dialog.close()
                    navigateTo(GamePlayView())
                }
            })
        })
        dialog.showAndWait()
    }
}
