package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.DeckComposition
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.Player
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.control.ScrollPane
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

class CreateSessionView : ViewBase() {
    private val stepPanel = VBox(10.0)
    private var currentStep = 0
    private val availablePlayers = AppDependencies.playerRepository.getAllPlayers().filter { !it.isPlaying }
    private var player1: Player? = null
    private var player2: Player? = null
    private var firstPlayerIdx = 1
    private var session: GameSession? = null
    private val hand1 = mutableMapOf(CardType.DEFUSE to 1)
    private val hand2 = mutableMapOf(CardType.DEFUSE to 1)
    private val availableCards = DeckComposition.CARDS.toMutableMap().apply {
        this[CardType.DEFUSE] = this[CardType.DEFUSE]!! - 2
    }
    private val drawPile = mutableListOf<CardType>()

    override val root: Parent = VBox(10.0).apply {
        padding = Insets(15.0)
        children.add(Label("CREATE NEW GAME SESSION").apply { styleClass.add("title") })
        children.add(ScrollPane().apply {
            content = stepPanel
            isFitToWidth = true
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        })
    }

    init { showStep0() }

    private fun showStep0() {
        currentStep = 0
        stepPanel.children.clear()

        if (availablePlayers.size < 2) {
            showNotEnoughPlayersMessage()
            return
        }

        with(stepPanel) {
            children.add(Label("Select players for new game:").apply { styleClass.add("section-title") })

            val (cb1, cb2) = buildPlayerComboBoxes()
            children.addAll(cb1, cb2)

            children.add(Label("Who will go first?").apply { styleClass.add("section-title") })
            val (rb1, rb2) = buildFirstPlayerRadios()
            children.addAll(rb1, rb2)

            val errorLabel = Label("").apply { styleClass.add("validation-error") }
            children.add(errorLabel)

            children.add(buildContinueButton(cb1, cb2, rb1, rb2, errorLabel))
        }
    }

    private fun showNotEnoughPlayersMessage() {
        with(stepPanel) {
            children.add(Label("Need at least 2 players to start a game"))
            children.add(Button("Back to menu").apply {
                styleClass.add("back-button")
                setOnAction { navigateTo(MainMenuView()) }
            })
        }
    }

    private fun playerListCell() = object : ListCell<Player>() {
        override fun updateItem(item: Player?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (item == null || empty) null else "${item.name} (${item.id})"
        }
    }

    private fun buildPlayerComboBoxes(): Pair<ComboBox<Player>, ComboBox<Player>> {
        val cellFactory = javafx.util.Callback<ListView<Player>, ListCell<Player>> { _ -> playerListCell() }

        val cb1 = ComboBox<Player>().apply {
            items.setAll(availablePlayers)
            buttonCell = playerListCell()
            this.cellFactory = cellFactory
            promptText = "Select Player 1"
        }
        val cb2 = ComboBox<Player>().apply {
            items.setAll(availablePlayers)
            buttonCell = playerListCell()
            this.cellFactory = cellFactory
            promptText = "Select Player 2"
        }
        return cb1 to cb2
    }

    private fun buildFirstPlayerRadios(): Pair<RadioButton, RadioButton> {
        val rb1 = RadioButton("Player 1").apply { isSelected = true }
        val rb2 = RadioButton("Player 2")
        val tg = ToggleGroup()
        rb1.toggleGroup = tg
        rb2.toggleGroup = tg
        return rb1 to rb2
    }

    private fun buildContinueButton(
        cb1: ComboBox<Player>,
        cb2: ComboBox<Player>,
        rb1: RadioButton,
        rb2: RadioButton,
        errorLabel: Label
    ) = Button("Continue").apply {
        setOnAction {
            val (p1, p2) = validatePlayerSelection(cb1, cb2, errorLabel) ?: return@setOnAction
            startGameSession(p1, p2, rb1.isSelected, errorLabel)
        }
    }

    private fun validatePlayerSelection(
        cb1: ComboBox<Player>,
        cb2: ComboBox<Player>,
        errorLabel: Label
    ): Pair<Player, Player>? {
        val p1 = cb1.value ?: run { errorLabel.text = "Select Player 1"; return null }
        val p2 = cb2.value ?: run { errorLabel.text = "Select Player 2"; return null }
        if (p1 == p2) { errorLabel.text = "Players must be different"; return null }
        return p1 to p2
    }

    private fun startGameSession(
        p1: Player,
        p2: Player,
        isFirstPlayer1: Boolean,
        errorLabel: Label
    ) {
        player1 = p1
        player2 = p2
        firstPlayerIdx = if (isFirstPlayer1) 1 else 2

        val firstId = if (isFirstPlayer1) p1.id else p2.id
        val secondId = if (isFirstPlayer1) p2.id else p1.id

        val sid = AppDependencies.engine.startNewSession(firstId, secondId)
        session = AppDependencies.gameRepository.getSession(sid)
        if (session == null) { errorLabel.text = "Failed to create session"; return }

        session!!.playerHands[p1.id] = hand1
        session!!.playerHands[p2.id] = hand2
        showStep1()
    }

    private fun showStep1() { currentStep = 1; buildCardSetup(player1!!, hand1) }
    private fun showStep2() { currentStep = 2; buildCardSetup(player2!!, hand2) }

    private fun buildCardSetup(player: Player, hand: MutableMap<CardType, Int>) {
        stepPanel.children.clear()
        val titleLabel = Label("").apply { styleClass.add("section-title") }
        val handPane = FlowPane(6.0, 6.0).apply {
            prefWrapLength = 300.0
            style = "-fx-padding: 10px; -fx-background-color: #fafafa; -fx-border-color: #ddd; " +
                    "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-min-height: 120px;"
        }
        val poolPane = FlowPane(6.0, 6.0).apply {
            prefWrapLength = 350.0
            style = "-fx-padding: 10px; -fx-background-color: #fafafa; -fx-border-color: #ddd; " +
                    "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-min-height: 120px;"
        }
        val errorLabel = Label("").apply { styleClass.add("validation-error") }

        rebuildCardDistributionUI(player, hand, titleLabel, handPane, poolPane)
        setupCardDragHandlers(player, hand, titleLabel, handPane, poolPane)

        stepPanel.children.addAll(
            titleLabel,
            buildCardLayout(handPane, poolPane),
            errorLabel,
            buildStepActionButtons(player, hand, errorLabel)
        )
    }

    private fun buildCardLayout(handPane: FlowPane, poolPane: FlowPane): HBox {
        return HBox(20.0).apply {
            children.addAll(
                VBox(6.0).apply {
                    children.addAll(Label(
                        "YOUR HAND — drag cards here from the pool").apply {
                            style = "-fx-font-weight: bold; -fx-font-size: 12px;" },
                        handPane)
                },
                VBox(6.0).apply {
                    children.addAll(Label(
                        "AVAILABLE CARDS — drag cards here to return").apply {
                            style = "-fx-font-weight: bold; -fx-font-size: 12px;" },
                        poolPane)
                }
            )
        }
    }

    private fun buildStepActionButtons(
        player: Player,
        hand: MutableMap<CardType, Int>,
        errorLabel: Label
    ): HBox {
        return HBox(8.0).apply {
            children.addAll(
                Button("Continue").apply {
                    setOnAction {
                        val size = hand.values.sum()
                        val hasDefuse = (hand[CardType.DEFUSE] ?: 0) >= 1
                        if (size != 8) {
                            errorLabel.text = "Need 8 cards (currently $size)"
                            return@setOnAction
                        }
                        if (!hasDefuse) {
                            errorLabel.text = "Need at least 1 DEFUSE"
                            return@setOnAction
                        }
                        session?.let { s -> s.playerHands[player.id] = hand }
                        if (currentStep == 1) showStep2() else showStep3()
                    }
                },
                Button("Cancel session").apply { setOnAction { cancelSession() } },
            )
        }
    }

    private fun cancelSession() {
        session?.let { s ->
            s.participants.forEach { pid ->
                AppDependencies.playerRepository.getPlayer(pid)?.isPlaying = false
            }
            AppDependencies.playerRepository.savePlayers()
            AppDependencies.gameRepository.removeSession(s.id)
        }
        navigateTo(MainMenuView())
    }

    private fun showStep3() {
        currentStep = 3; stepPanel.children.clear()
        val poolPane = FlowPane(6.0, 6.0).apply {
            prefWrapLength = 500.0
            styleClass.add("setup-panel")
            minHeight = 100.0
        }
        val deckVBox = VBox(5.0).apply { styleClass.add("setup-panel") }
        val errorLabel = Label("").apply { styleClass.add("validation-error") }

        setupPoolDropFromDeck(poolPane, deckVBox)
        refreshDeckSetupUI(poolPane, deckVBox)

        stepPanel.children.addAll(
            Label("Available cards (click to add to draw pile):").apply {
                styleClass.add("section-title") },
            poolPane,
            Label("Draw pile:").apply { styleClass.add("section-title") },
            deckVBox,
            errorLabel,
            buildStep3ActionButtons(poolPane, deckVBox, errorLabel)
        )
    }

    private fun buildStep3ActionButtons(poolPane: FlowPane, deckVBox: VBox, errorLabel: Label): HBox {
        return HBox(8.0).apply {
            children.addAll(
                Button("Start session").apply {
                    setOnAction { startSession(errorLabel) }
                },
                Button("Reset").apply {
                    setOnAction {
                        drawPile.forEach { availableCards[it] = (availableCards[it] ?: 0) + 1 }
                        drawPile.clear(); refreshDeckSetupUI(poolPane, deckVBox)
                    }
                },
                Button("Cancel").apply { setOnAction { cancelSession() } },
            )
        }
    }

    private fun startSession(errorLabel: Label) {
        val remaining = availableCards.filterValues { it > 0 }
        if (remaining.isNotEmpty()) {
            errorLabel.text = "Pool not empty (${remaining.values.sum()} cards remaining)"
            return
        }
        val s = session ?: return
        s.drawPile.clear(); s.drawPile.addAll(drawPile)
        val v = AppDependencies.ruleValidator.validateDrawPile(
            s,
            DeckComposition.CARDS,
            availableCards
        )
        if (!v.isValid) {
            errorLabel.text = v.errors.joinToString("\n")
            return
        }
        AppDependencies.activeSessionId = s.id
        AppDependencies.gameRepository.saveSessions()
        navigateTo(GamePlayView())
    }

    private fun rebuildCardDistributionUI(
        player: Player,
        hand: MutableMap<CardType, Int>,
        titleLabel: Label,
        handPane: FlowPane,
        poolPane: FlowPane,
    ) {
        updateTitleLabel(player, hand, titleLabel)
        rebuildHandPane(hand, handPane)
        rebuildPoolPane(poolPane)
    }

    private fun updateTitleLabel(player: Player, hand: Map<CardType, Int>, titleLabel: Label) {
        titleLabel.text = "${player.name.uppercase()} — Select cards (${hand.values.sum()}/8)"
    }

    private fun rebuildHandPane(hand: MutableMap<CardType, Int>, handPane: FlowPane) {
        handPane.children.clear()
        if (hand.isEmpty()) {
            handPane.children.add(Label("(empty)").apply { style = "-fx-text-fill: #999;" })
        } else {
            hand.forEach { (type, count) ->
                val locked = type == CardType.DEFUSE && count <= 1
                val tile = cardTile(type, count, isLocked = locked)
                if (!locked) {
                    tile.setOnDragDetected { _ ->
                        val db = tile.startDragAndDrop(TransferMode.MOVE)
                        val cc = ClipboardContent()
                        cc.putString("hand:${type.name}")
                        db.setContent(cc)
                        db.dragView = tile.snapshot(null, null)
                    }
                }
                handPane.children.add(tile)
            }
        }
    }

    private fun rebuildPoolPane(poolPane: FlowPane) {
        val selectable = availableCards.filter { it.value > 0 && it.key != CardType.EXPLODING_KITTEN }
        poolPane.children.clear()
        if (selectable.isEmpty()) {
            poolPane.children.add(Label("No cards available!").apply { style = "-fx-text-fill: #999;" })
        } else {
            selectable.forEach { (type, count) ->
                val tile = cardTile(type, count, maxTotal = DeckComposition.CARDS[type])
                tile.setOnDragDetected { _ ->
                    val db = tile.startDragAndDrop(TransferMode.MOVE)
                    val cc = ClipboardContent()
                    cc.putString("pool:${type.name}")
                    db.setContent(cc)
                    db.dragView = tile.snapshot(null, null)
                }
                poolPane.children.add(tile)
            }
        }
    }

    private fun setupCardDragHandlers(
        player: Player,
        hand: MutableMap<CardType, Int>,
        titleLabel: Label,
        handPane: FlowPane,
        poolPane: FlowPane,
    ) {
        setupHandPaneDragHandlers(player, hand, titleLabel, handPane, poolPane)
        setupPoolPaneDragHandlers(player, hand, titleLabel, handPane, poolPane)
    }

    private fun setupHandPaneDragHandlers(
        player: Player,
        hand: MutableMap<CardType, Int>,
        titleLabel: Label,
        handPane: FlowPane,
        poolPane: FlowPane,
    ) {
        val defaultStyle = handPane.style
        handPane.setOnDragOver { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("pool:") && hand.values.sum() < 8) {
                event.acceptTransferModes(TransferMode.MOVE)
                handPane.style = "$defaultStyle; -fx-border-color: #27ae60; -fx-background-color: #f0fdf4;"
            }
            event.consume()
        }
        handPane.setOnDragExited { handPane.style = defaultStyle }
        handPane.setOnDragDropped { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("pool:") && hand.values.sum() < 8) {
                val type = CardType.valueOf(data.removePrefix("pool:"))
                val count = availableCards[type] ?: return@setOnDragDropped
                if (count > 0) {
                    hand[type] = (hand[type] ?: 0) + 1; availableCards[type] = count - 1
                    rebuildCardDistributionUI(player, hand, titleLabel, handPane, poolPane)
                    event.isDropCompleted = true
                }
            }
            handPane.style = defaultStyle; event.consume()
        }
    }

    private fun setupPoolPaneDragHandlers(
        player: Player,
        hand: MutableMap<CardType, Int>,
        titleLabel: Label,
        handPane: FlowPane,
        poolPane: FlowPane,
    ) {
        val defaultStyle = poolPane.style
        poolPane.setOnDragOver { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("hand:")) {
                val type = CardType.valueOf(data.removePrefix("hand:"))
                val hc = hand[type] ?: 0
                if (type != CardType.DEFUSE || hc > 1) {
                    event.acceptTransferModes(TransferMode.MOVE)
                    poolPane.style = "$defaultStyle; -fx-border-color: #e67e22; -fx-background-color: #fef5e7;"
                }
            }
            event.consume()
        }
        poolPane.setOnDragExited { poolPane.style = defaultStyle }
        poolPane.setOnDragDropped { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("hand:")) {
                val type = CardType.valueOf(data.removePrefix("hand:"))
                val hc = hand[type] ?: 0
                if (hc > 0 && (type != CardType.DEFUSE || hc > 1)) {
                    if (hc > 1) hand[type] = hc - 1 else hand.remove(type)
                    availableCards[type] = (availableCards[type] ?: 0) + 1
                    rebuildCardDistributionUI(player, hand, titleLabel, handPane, poolPane)
                    event.isDropCompleted = true
                }
            }
            poolPane.style = defaultStyle; event.consume()
        }
    }

    private fun refreshDeckSetupUI(poolPane: FlowPane, deckVBox: VBox) {
        refreshPoolPane(poolPane, deckVBox)
        refreshDeckPile(poolPane, deckVBox)
    }

    private fun refreshPoolPane(poolPane: FlowPane, deckVBox: VBox) {
        poolPane.children.clear()
        val selectable = availableCards.filterValues { it > 0 }
        if (selectable.isEmpty()) {
            poolPane.children.add(Label("(no cards left)").apply { style = "-fx-text-fill: #999;" })
        } else {
            selectable.forEach { (type, count) ->
                val tile = cardTile(type, count, maxTotal = DeckComposition.CARDS[type])
                tile.setOnMouseClicked {
                    drawPile.add(type)
                    availableCards[type] = (availableCards[type] ?: 0) - 1
                    refreshDeckSetupUI(poolPane, deckVBox)
                }
                poolPane.children.add(tile)
            }
        }
    }

    private fun refreshDeckPile(poolPane: FlowPane, deckVBox: VBox) {
        deckVBox.children.clear()
        deckVBox.children.add(buildDeckTitle())
        if (drawPile.isEmpty()) {
            deckVBox.children.add(Label("(empty)").apply { style = "-fx-text-fill: #999;" })
        } else {
            drawPile.forEachIndexed { i, cardType ->
                deckVBox.children.add(buildDeckTile(cardType, i, poolPane, deckVBox))
            }
        }
    }

    private fun buildDeckTitle(): Label {
        val text = "Draw pile (${drawPile.size} cards)" +
            if (drawPile.isNotEmpty()) " — drag to reorder, double-click or drag to pool to remove" else ""
        return Label(text).apply { style = "-fx-font-weight: bold;" }
    }

    private fun buildDeckTile(cardType: CardType, index: Int, poolPane: FlowPane, deckVBox: VBox): HBox {
        return HBox().apply {
            styleClass.addAll("deck-tile", "card-${cardType.name}")
            children.add(Label(cardType.name.split("_").joinToString(" ")))

            setOnMouseClicked { event ->
                if (event.clickCount == 2) {
                    drawPile.removeAt(index)
                    availableCards[cardType] = (availableCards[cardType] ?: 0) + 1
                    refreshDeckSetupUI(poolPane, deckVBox)
                }
            }

            setOnDragDetected { event ->
                val db = startDragAndDrop(TransferMode.MOVE)
                val cc = ClipboardContent()
                cc.putString("deck:$index")
                db.setContent(cc)
                db.dragView = snapshot(null, null)
                event.consume()
            }

            setOnDragOver { event ->
                val data = event.dragboard.string
                if (data != null && data.startsWith("deck:")) {
                    event.acceptTransferModes(TransferMode.MOVE)
                }
                event.consume()
            }

            setOnDragDropped { event ->
                val data = event.dragboard.string
                if (data != null && data.startsWith("deck:")) {
                    val src = data.removePrefix("deck:").toInt()
                    if (src != index) {
                        val card = drawPile.removeAt(src)
                        drawPile.add(if (src < index) index - 1 else index, card)
                        refreshDeckSetupUI(poolPane, deckVBox)
                    }
                    event.isDropCompleted = true
                }
                event.consume()
            }
        }
    }

    private fun setupPoolDropFromDeck(poolPane: FlowPane, deckVBox: VBox) {
        poolPane.setOnDragOver { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("deck:")) {
                event.acceptTransferModes(TransferMode.MOVE)
            }
            event.consume()
        }
        poolPane.setOnDragDropped { event ->
            val data = event.dragboard.string
            if (data != null && data.startsWith("deck:")) {
                val src = data.removePrefix("deck:").toInt()
                val card = drawPile.removeAt(src)
                availableCards[card] = (availableCards[card] ?: 0) + 1
                refreshDeckSetupUI(poolPane, deckVBox)
                event.isDropCompleted = true
            }
            event.consume()
        }
    }
}

private fun cardTile(cardType: CardType, count: Int, maxTotal: Int? = null, isLocked: Boolean = false): VBox {
    return VBox(3.0).apply {
        padding = Insets(8.0, 14.0, 8.0, 14.0)
        styleClass.addAll("card-tile", "card-${cardType.name}")
        if (isLocked) styleClass.add("card-locked-tile") else styleClass.add("card-clickable")
        minWidth = 80.0; minHeight = 55.0
        alignment = javafx.geometry.Pos.CENTER
        children.add(Label(cardType.name.split("_").joinToString(" ")).apply {
            styleClass.add("card-name")
        })
        val countText = when {
            isLocked -> "($count) locked"
            maxTotal != null -> "$count / $maxTotal"
            count > 0 -> "($count)"
            else -> null
        }
        if (countText != null) {
            children.add(Label(countText).apply { styleClass.add("card-count") })
        }
    }
}
