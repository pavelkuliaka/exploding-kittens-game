package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.Player
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.VBox

class PlayersListView : ViewBase() {
    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("PLAYERS LIST").apply { styleClass.add("title") })

        val players = AppDependencies.playerRepository.getAllPlayers()
        if (players.isEmpty()) {
            children.add(Label("No players found"))
        } else {
            val table = TableView<Player>().apply {
                prefHeight = 350.0
                columns.addAll(
                    TableColumn<Player, String>("Name").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.name) }
                    },
                    TableColumn<Player, String>("ID").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper((it.value.id.toString())) }
                    },
                    TableColumn<Player, String>("Status").apply {
                        setCellValueFactory {
                            ReadOnlyObjectWrapper(if (it.value.isPlaying) "Playing" else "Idle")
                        }
                    },
                    TableColumn<Player, String>("Total Games").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.stats.totalGames.toString()) }
                    },
                    TableColumn<Player, String>("Wins").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.stats.wins.toString()) }
                    },
                    TableColumn<Player, String>("Losses").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.stats.losses.toString()) }
                    },
                    TableColumn<Player, String>("Win Rate").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper("${it.value.stats.winRate}%") }
                    },
                    TableColumn<Player, String>("Defused").apply {
                        setCellValueFactory { ReadOnlyObjectWrapper(it.value.stats.defused.toString()) }
                    },
                )
            }
            table.items.setAll(players)
            children.add(table)
        }

        children.add(Button("Back to menu").apply {
            styleClass.add("back-button")
            setOnAction { navigateTo(MainMenuView()) }
        })
    }
}
