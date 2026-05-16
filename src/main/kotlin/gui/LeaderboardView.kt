package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.Player
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

class LeaderboardView : ViewBase() {
    private val leaderboardData = FXCollections.observableArrayList<Pair<Int, Player>>()

    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)

        children.add(Label("LEADERBOARD").apply { styleClass.add("title") })

        val table = TableView<Pair<Int, Player>>().apply {
            prefHeight = 350.0
            columns.addAll(
                TableColumn<Pair<Int, Player>, String>("Rank").apply {
                    setCellValueFactory { ReadOnlyObjectWrapper(it.value.first.toString()) }
                },
                TableColumn<Pair<Int, Player>, String>("Name").apply {
                    setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.name) }
                },
                TableColumn<Pair<Int, Player>, String>("ID").apply {
                    setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.id.toString()) }
                },
                TableColumn<Pair<Int, Player>, String>("Wins").apply {
                    setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.stats.wins.toString()) }
                },
                TableColumn<Pair<Int, Player>, String>("Total Games").apply {
                    setCellValueFactory { ReadOnlyObjectWrapper(it.value.second.stats.totalGames.toString()) }
                },
            )
            items = leaderboardData
        }

        val nField = TextField().apply {
            text = "10"
            prefWidth = 80.0
        }

        children.addAll(
            HBox(8.0).apply {
                children.addAll(
                    Label("Number of players to display:"),
                    nField,
                    Button("Show").apply {
                        setOnAction {
                            val n = nField.text.toUIntOrNull() ?: return@setOnAction
                            val result = AppDependencies.statisticsService.getLeaderboard(n)
                            leaderboardData.setAll(result.mapIndexed { idx, p -> (idx + 1) to p })
                        }
                    }
                )
            },
            Label("(Click 'Show' to refresh)").apply { style = "-fx-font-size: 11px; -fx-text-fill: #888;" },
            table,
            Button("Back to menu").apply {
                styleClass.add("back-button")
                setOnAction { navigateTo(MainMenuView()) }
            }
        )
    }
}
