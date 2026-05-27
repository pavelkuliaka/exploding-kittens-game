package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.VBox

class SessionsListView : ViewBase() {
    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("SESSIONS LIST").apply { styleClass.add("title") })

        val sessions = AppDependencies.gameRepository.sessions.toList().sortedBy { (_, s) -> s.status.name }
        if (sessions.isEmpty()) {
            children.add(Label("No sessions found"))
        } else {
            val table = TableView<Pair<java.util.UUID, GameSession>>().apply {
                prefHeight = 300.0
                columns.addAll(
                    TableColumn<Pair<java.util.UUID, GameSession>, String>("Status").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.status.name) }
                    },
                    TableColumn<Pair<java.util.UUID, GameSession>, String>("ID").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.first.toString()) }
                    },
                    TableColumn<Pair<java.util.UUID, GameSession>, String>("Participants").apply {
                        setCellValueFactory {
                            val names = it.value.second.participants.joinToString(", ") { pid ->
                                val player = AppDependencies.playerRepository.getPlayer(pid)
                                "${player?.name ?: "?"} ($pid)"
                            }
                            ReadOnlyObjectWrapper(names)
                        }
                    },
                    TableColumn<Pair<java.util.UUID, GameSession>, String>("Winner").apply {
                        setCellValueFactory {
                            val session = it.value.second
                            val winner = if (session.status == GameStatus.FINISHED) {
                                session.winnerId?.let { wid ->
                                    AppDependencies.playerRepository.getPlayer(wid)?.name ?: "none"
                                } ?: "none"
                            } else ""
                            ReadOnlyObjectWrapper(winner)
                        }
                    },
                    TableColumn<Pair<java.util.UUID, GameSession>, String>("Turns").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.turns.size.toString()) }
                    },
                )
            }
            table.items.setAll(sessions)
            children.add(table)
        }

        children.add(Button("Back to menu").apply {
            styleClass.add("back-button")
            setOnAction { navigateTo(MainMenuView()) }
        })
    }
}
