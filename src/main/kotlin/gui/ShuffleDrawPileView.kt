package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.CardType
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

class ShuffleDrawPileView : ViewBase() {
    val newPile = mutableListOf<CardType>()
    val pool = mutableListOf<CardType>()
    private var confirmed = false

    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)
        children.add(Label("SHUFFLE DRAW PILE").apply { styleClass.add("title") })

        val pileBox = VBox(4.0)
        val poolBox = VBox(4.0)
        val choiceField = TextField().apply { promptText = "#"; prefWidth = 60.0 }

        fun updateUI() {
            pileBox.children.clear()
            pileBox.children.add(Label("New pile (${newPile.size} cards):").apply { style = "-fx-font-weight: bold;" })
            if (newPile.isEmpty()) {
                pileBox.children.add(Label("(empty)"))
            } else {
                newPile.forEachIndexed { idx, card ->
                    pileBox.children.add(Label("  ${idx + 1}. $card"))
                }
            }

            val grouped = pool.groupBy { it }.entries.toList()
            poolBox.children.clear()
            poolBox.children.add(Label("Remaining pool (${pool.size} cards):").apply { style = "-fx-font-weight: bold;" })
            if (grouped.isEmpty()) {
                poolBox.children.add(Label("(empty)"))
            } else {
                grouped.forEachIndexed { idx, entry ->
                    poolBox.children.add(Label("  ${idx + 1}. ${entry.key} (${entry.value.size})"))
                }
            }
        }
        updateUI()

        children.addAll(
            pileBox, poolBox,
            HBox(8.0).apply {
                children.addAll(
                    choiceField,
                    Button("Add from pool").apply {
                        setOnAction {
                            val grouped = pool.groupBy { it }.entries.toList()
                            if (grouped.isEmpty()) return@setOnAction
                            val choice = choiceField.text.toIntOrNull() ?: return@setOnAction
                            if (choice !in 1..grouped.size) return@setOnAction
                            val card = grouped[choice - 1].key
                            val idx = pool.indexOf(card)
                            if (idx >= 0) {
                                pool.removeAt(idx)
                                newPile.add(card)
                                choiceField.text = ""
                                updateUI()
                            }
                        }
                    },
                    Button("Reset").apply {
                        setOnAction {
                            newPile.forEach { pool.add(it) }
                            newPile.clear()
                            updateUI()
                        }
                    },
                )
            },
            HBox(8.0).apply {
                children.addAll(
                    Button("Confirm").apply {
                        setOnAction {
                            if (pool.isNotEmpty()) return@setOnAction
                            confirmed = true
                            navigateTo(GamePlayView())
                        }
                    },
                    Button("Cancel").apply { setOnAction { navigateTo(GamePlayView()) } },
                )
            },
        )
    }

    fun result(): List<CardType>? = if (confirmed) newPile.toList() else null
}
