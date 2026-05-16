package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.*
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import java.util.UUID

class GamePlayView : ViewBase() {
    private val session get() = AppDependencies.activeSession ?: error("No active session")

    private val statePanel = VBox(4.0).apply { styleClass.add("game-state-panel") }
    private val currentHandPanel = VBox(4.0)
    private val opponentHandPanel = VBox(4.0)
    private val discardPanel = VBox(4.0)
    private val turnsPanel = VBox(4.0)
    private val actionsPanel = VBox(6.0)
    private val inputPanel = VBox(6.0)
    private val messageLabel = Label("")

    override val root = ScrollPane().apply {
        content = VBox(8.0).apply {
            padding = Insets(10.0)
            children.addAll(
                HBox(8.0).apply {
                    children.addAll(
                        Button("Back to main menu").apply {
                            setOnAction {
                                navigateTo(MainMenuView())
                            }
                        },
                        messageLabel
                    )
                },
                Separator(),
                statePanel,
                HBox(30.0).apply {
                    children.addAll(
                        VBox(4.0).apply {
                            children.addAll(
                                Label("").apply {
                                    id = "current-player-label"
                                    style = "-fx-font-weight: bold; -fx-font-size: 14px;"},
                                currentHandPanel
                            )
                        },
                        VBox(4.0).apply {
                            children.addAll(
                                Label("").apply {
                                    id = "opponent-player-label"
                                    style = "-fx-font-weight: bold; -fx-font-size: 14px;" },
                                opponentHandPanel
                            )
                        },
                    )
                },
                HBox(30.0).apply {
                    children.addAll(
                        VBox(4.0).apply {
                            children.addAll(Label("Discard pile:").apply {
                                style = "-fx-font-weight: bold;" },
                                discardPanel
                            )
                        },
                        VBox(4.0).apply {
                            children.addAll(Label("Recent turns:").apply {
                                style = "-fx-font-weight: bold;" },
                                turnsPanel
                            )
                        },
                    )
                },
                Label("AVAILABLE ACTIONS").apply { styleClass.add("section-title") },
                actionsPanel,
                inputPanel
            )
        }
    }

    init {
        if (AppDependencies.activeSessionId == null) {
            navigateTo(MainMenuView())
        } else {
            refresh()
        }
    }

    private fun refresh() {
        if (session.status != GameStatus.ACTIVE) {
            messageLabel.text = "Game session is finished"
            return
        }

        val currentPlayerId = session.whoseTurn ?: run {
            messageLabel.text = "No current player is set"
            return
        }
        val currentPlayer = AppDependencies.playerRepository.getPlayer(currentPlayerId) ?: return
        val opponentId = session.participants.first { it != currentPlayerId }
        val opponent = AppDependencies.playerRepository.getPlayer(opponentId) ?: return
        val topCard = if (session.drawPile.isNotEmpty()) session.drawPile.first() else null

        if (session.mustDefuse) {
            val hand = session.playerHands[currentPlayerId] ?: mutableMapOf()
            if ((hand[CardType.DEFUSE] ?: 0) < 1) {
                showGameOver("BOOM! ${currentPlayer.name} drew an EXPLODING KITTEN " +
                        "and has no DEFUSE!\n${opponent.name} WINS!")
                return
            }
        }

        statePanel.children.clear()
        if (session.mustDefuse) {
            statePanel.children.add(Label("${currentPlayer.name} MUST DEFUSE").apply {
                styleClass.add("must-defuse")
            })
        } else {
            statePanel.children.add(Label("Turn: ${currentPlayer.name} (attack turns " +
                    "remaining: ${session.attackTurnsRemaining})"))
        }
        statePanel.children.add(Label("Draw pile: ${session.drawPile.size} cards${if (topCard != null) " " +
                "(top: $topCard)" else ""}"))

        currentHandPanel.children.clear()
        currentHandPanel.children.add(Label("${currentPlayer.name}:").apply {
            style = "-fx-font-weight: bold; -fx-font-size: 14px;"
        })
        val curHand = session.playerHands[currentPlayerId] ?: mutableMapOf()
        if (curHand.isEmpty()) currentHandPanel.children.add(Label("(empty)"))
        else curHand.forEach { (type, count) -> currentHandPanel.children.add(Label("$type ($count)").apply {
            styleClass.add("hand-label")
        }) }

        opponentHandPanel.children.clear()
        opponentHandPanel.children.add(Label("${opponent.name}:").apply {
            style = "-fx-font-weight: bold; -fx-font-size: 14px;"
        })
        val oppHand = session.playerHands[opponentId] ?: mutableMapOf()
        if (oppHand.isEmpty()) opponentHandPanel.children.add(Label("(empty)"))
        else oppHand.forEach { (type, count) -> opponentHandPanel.children.add(Label("$type ($count)").apply {
            styleClass.add("opponent-hand-label")
        }) }

        discardPanel.children.clear()
        if (session.discardPile.isEmpty()) discardPanel.children.add(Label("(empty)"))
        else session.discardPile.forEach {
                (type, count) -> discardPanel.children.add(Label("$type ($count)").apply {
            styleClass.add("discard-label")
        })
        }

        turnsPanel.children.clear()
        if (session.turns.isEmpty()) turnsPanel.children.add(Label("(none)"))
        else {
            val recent = session.turns.takeLast(5)
            val startIdx = session.turns.size - recent.size
            recent.forEachIndexed { idx, turn ->
                val turnNum = startIdx + idx + 1
                val turnPlayer = AppDependencies.playerRepository.getPlayer(turn.playerId)
                turnsPanel.children.add(Label("  $turnNum. ${turnPlayer?.name}: ${turnDescription(turn)}"))
            }
        }

        actionsPanel.children.clear()
        inputPanel.children.clear()
        inputPanel.isVisible = false

        val actions = buildActions(session, currentPlayerId, opponentId, AppDependencies.playerRepository)
        if (actions.isEmpty()) {
            actionsPanel.children.add(Label("No actions available"))
        } else {
            actions.forEachIndexed { idx, action ->
                actionsPanel.children.add(Button("${idx + 1}. ${action.label}").apply {
                    styleClass.add("action-button")
                    setOnAction { handleAction(action, currentPlayerId, opponentId) }
                })
            }
        }
    }

    private fun handleAction(action: ActionOption, currentPlayerId: UUID, opponentId: UUID) {
        when (action.type) {
            ActionType.DRAW_CARD, ActionType.ATTACK, ActionType.SKIP, ActionType.SEE_THE_FUTURE -> {
                executeAction(action, currentPlayerId)
            }
            ActionType.DEFUSE -> showDefuseInput(currentPlayerId)
            ActionType.FAVOR -> showFavorInput(currentPlayerId, opponentId)
            ActionType.PLAY_DOUBLE -> showPlayDoubleInput(currentPlayerId, opponentId)
            ActionType.PLAY_TRIPLE -> showPlayTripleInput(currentPlayerId)
            ActionType.NOPE -> showNopeInput(action)
            ActionType.SHUFFLE -> showShuffleDialog(currentPlayerId)
        }
    }

    private fun showDefuseInput(playerId: UUID) {
        inputPanel.isVisible = true; inputPanel.children.clear()
        inputPanel.children.add(Label("Position to insert Exploding Kitten (0-${session.drawPile.size}):"))
        val posField = TextField().apply { promptText = "0-${session.drawPile.size}"; prefWidth = 100.0 }
        inputPanel.children.add(posField)
        inputPanel.children.add(HBox(8.0).apply {
            children.addAll(
                Button("Confirm").apply {
                    setOnAction {
                        val pos = posField.text.toIntOrNull()
                        if (pos == null || pos < 0 || pos > session.drawPile.size) return@setOnAction
                        doTurn(Turn.Defuse(playerId, pos))
                    }
                },
                Button("Cancel").apply { setOnAction { refresh() } },
            )
        })
    }

    private fun showFavorInput(playerId: UUID, opponentId: UUID) {
        inputPanel.isVisible = true; inputPanel.children.clear()
        val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()
        if (opponentHand.isEmpty()) { refresh(); return }
        inputPanel.children.add(Label("Choose a card to take from opponent:"))
        val cards = opponentHand.entries.toList()
        val tg = ToggleGroup()
        cards.forEachIndexed { idx, (type, count) ->
            inputPanel.children.add(RadioButton("${idx + 1}. $type ($count)").apply { toggleGroup = tg })
        }
        inputPanel.children.add(HBox(8.0).apply {
            children.addAll(
                Button("Confirm").apply {
                    setOnAction {
                        val sel = tg.selectedToggle ?: return@setOnAction
                        val idx = tg.toggles.indexOf(sel)
                        if (idx !in cards.indices) return@setOnAction
                        doTurn(Turn.Favor(playerId, cards[idx].key))
                    }
                },
                Button("Cancel").apply { setOnAction { refresh() } },
            )
        })
    }

    private fun showPlayDoubleInput(playerId: UUID, opponentId: UUID) {
        inputPanel.isVisible = true; inputPanel.children.clear()
        val hand = session.playerHands[playerId] ?: mutableMapOf()
        val catCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
            .filter { (hand[it] ?: 0) >= 2 }
        if (catCards.isEmpty()) { refresh(); return }
        inputPanel.children.add(Label("Choose pair to play:"))
        val cardTg = ToggleGroup()
        catCards.forEachIndexed { idx, card ->
            inputPanel.children.add(RadioButton("${idx + 1}. $card").apply { toggleGroup = cardTg })
        }

        val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()
        if (opponentHand.isEmpty()) { refresh(); return }
        inputPanel.children.add(Label("Choose a card to steal from opponent:"))
        val stealTg = ToggleGroup()
        val stealCards = opponentHand.entries.toList()
        stealCards.forEachIndexed { idx, (type, count) ->
            inputPanel.children.add(RadioButton("${idx + 1}. $type ($count)").apply { toggleGroup = stealTg })
        }

        inputPanel.children.add(HBox(8.0).apply {
            children.addAll(
                Button("Confirm").apply {
                    setOnAction {
                        val cardSel = cardTg.selectedToggle; val stealSel = stealTg.selectedToggle
                        if (cardSel == null || stealSel == null) return@setOnAction
                        val ci = cardTg.toggles.indexOf(cardSel); val si = stealTg.toggles.indexOf(stealSel)
                        if (ci !in catCards.indices || si !in stealCards.indices) return@setOnAction
                        doTurn(Turn.PlayDouble(playerId, catCards[ci], stealCards[si].key))
                    }
                },
                Button("Cancel").apply { setOnAction { refresh() } },
            )
        })
    }

    private fun showPlayTripleInput(playerId: UUID) {
        inputPanel.isVisible = true; inputPanel.children.clear()
        val hand = session.playerHands[playerId] ?: mutableMapOf()
        val tripleCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
            .filter { (hand[it] ?: 0) >= 3 }
        if (tripleCards.isEmpty()) { refresh(); return }
        inputPanel.children.add(Label("Choose triple to play:"))
        val tg = ToggleGroup()
        tripleCards.forEachIndexed { idx, card ->
            inputPanel.children.add(RadioButton("${idx + 1}. $card").apply { toggleGroup = tg })
        }
        inputPanel.children.add(HBox(8.0).apply {
            children.addAll(
                Button("Confirm").apply {
                    setOnAction {
                        val sel = tg.selectedToggle ?: return@setOnAction
                        val idx = tg.toggles.indexOf(sel)
                        if (idx !in tripleCards.indices) return@setOnAction
                        doTurn(Turn.PlayTriple(playerId, tripleCards[idx]))
                    }
                },
                Button("Cancel").apply { setOnAction { refresh() } },
            )
        })
    }

    private fun showNopeInput(action: ActionOption) {
        val nopePlayerId = action.playerIdFromLabel(session) ?: return
        inputPanel.isVisible = true; inputPanel.children.clear()
        inputPanel.children.add(Label("Choose a turn to Nope (0-${session.turns.size - 1}):"))
        session.turns.forEachIndexed { idx, turn ->
            val isTargetable = turn !is Turn.Defuse && turn !is Turn.DrawCard
            val marker = if (!isTargetable) " [locked]" else ""
            inputPanel.children.add(Label("  $idx. ${turnDescription(turn)}$marker"))
        }
        val targetField = TextField().apply { promptText = "Turn index"; prefWidth = 100.0 }
        inputPanel.children.add(targetField)
        inputPanel.children.add(HBox(8.0).apply {
            children.addAll(
                Button("Confirm").apply {
                    setOnAction {
                        val targetIdx = targetField.text.toIntOrNull()
                        if (targetIdx == null || targetIdx !in session.turns.indices) return@setOnAction
                        val targetTurn = session.turns[targetIdx]
                        if (targetTurn is Turn.Defuse || targetTurn is Turn.DrawCard) return@setOnAction
                        doTurn(Turn.Nope(nopePlayerId, targetIdx))
                    }
                },
                Button("Cancel").apply { setOnAction { refresh() } },
            )
        })
    }

    private fun showShuffleDialog(playerId: UUID) {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.title = "Shuffle Draw Pile"
        val newPile = mutableListOf<CardType>()
        val pool = session.drawPile.toMutableList()
        var confirmed = false

        val pileBox = VBox(4.0); val poolBox = VBox(4.0); val choiceField = TextField()

        fun update() {
            pileBox.children.clear()
            pileBox.children.add(Label("New pile (${newPile.size} cards):").apply {
                style = "-fx-font-weight: bold;"
            })
            if (newPile.isEmpty()) pileBox.children.add(Label("(empty)"))
            else newPile.forEachIndexed { i, c -> pileBox.children.add(Label("  ${i + 1}. $c")) }

            val g = pool.groupBy { it }.entries.toList()
            poolBox.children.clear()
            poolBox.children.add(Label("Remaining pool (${pool.size} cards):").apply {
                style = "-fx-font-weight: bold;"
            })
            if (g.isEmpty()) poolBox.children.add(Label("(empty)"))
            else g.forEachIndexed { i, e -> poolBox.children.add(Label("  ${i + 1}. ${e.key} (${e.value.size})"))}
        }
        update()

        dialog.scene = Scene(VBox(10.0).apply {
            padding = Insets(15.0)
            children.addAll(
                Label("SHUFFLE DRAW PILE").apply { styleClass.add("title") },
                pileBox, poolBox,
                HBox(8.0).apply {
                    children.addAll(
                        choiceField.apply { promptText = "#"; prefWidth = 60.0 },
                        Button("Add from pool").apply {
                            setOnAction {
                                val g = pool.groupBy { it }.entries.toList()
                                if (g.isEmpty()) return@setOnAction
                                val c = choiceField.text.toIntOrNull() ?: return@setOnAction
                                if (c !in 1..g.size) return@setOnAction
                                val card = g[c - 1].key; val idx = pool.indexOf(card)
                                if (idx >= 0) {
                                    pool.removeAt(idx)
                                    newPile.add(card)
                                    choiceField.text = ""; update()
                                }
                            }
                        },
                        Button("Reset").apply {
                            setOnAction { newPile.forEach { pool.add(it) }; newPile.clear(); update() }
                        },
                        Button("Confirm").apply {
                            setOnAction { if (pool.isEmpty()) { confirmed = true; dialog.close() } }
                        },
                        Button("Cancel").apply { setOnAction { dialog.close() } },
                    )
                },
            )
        })
        dialog.showAndWait()

        if (confirmed) doTurn(Turn.Shuffle(playerId, newPile.toList()))
    }

    private fun executeAction(action: ActionOption, currentPlayerId: UUID) {
        val turn: Turn? = when (action.type) {
            ActionType.DRAW_CARD -> {
                val card = session.drawPile.firstOrNull() ?: return
                Turn.DrawCard(currentPlayerId, card)
            }
            ActionType.ATTACK -> Turn.Attack(currentPlayerId)
            ActionType.SKIP -> Turn.Skip(currentPlayerId)
            ActionType.SEE_THE_FUTURE -> Turn.SeeTheFuture(currentPlayerId)
            else -> null
        }
        if (turn != null) doTurn(turn)
    }

    private fun doTurn(turn: Turn) {
        val success = AppDependencies.engine.addTurn(session.id, turn)
        inputPanel.isVisible = false; inputPanel.children.clear()

        if (!success) {
            messageLabel.text = "Invalid move! Turn rejected."
            return
        }
        messageLabel.text = ""

        if (session.status != GameStatus.ACTIVE) {
            val winner = session.winnerId?.let {
                AppDependencies.playerRepository.getPlayer(it)?.name
            } ?: "Unknown"
            showGameOver("Game Over!\nWinner: $winner")
        } else {
            refresh()
        }
    }

    private fun showGameOver(message: String) {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.title = "Game Over"
        dialog.scene = Scene(VBox(15.0).apply {
            padding = Insets(20.0)
            children.addAll(
                Label(message).apply { styleClass.add("title") },
                Button("Back to menu").apply {
                    setOnAction { dialog.close(); navigateTo(MainMenuView()) }
                },
            )
        })
        dialog.showAndWait()
    }
}
