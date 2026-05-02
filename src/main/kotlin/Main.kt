package com.github.pavelkuliaka

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.ActionType
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.DeckComposition
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator
import java.util.UUID

import java.util.Scanner
import kotlin.system.exitProcess

val scanner = Scanner(System.`in`)
fun readLine(): String? = try { scanner.nextLine() } catch (_: NoSuchElementException) { null }
fun clear() = print("\u001B[2J\u001B[H")

fun main() {
    val gameRepository = JsonGameRepository("games.json")
    if (!gameRepository.loadSessions()) {
        println("Information about the game sessions has not been uploaded")
    }

    val playerRepository = JsonPlayerRepository("players.json")
    if (!playerRepository.loadPlayers()) {
        println("Player information has not been uploaded")
    }

    val ruleValidator = RuleValidator()
    val statisticsService = StatisticsService(playerRepository)
    val engine = GameAdminEngine(gameRepository, playerRepository, ruleValidator, statisticsService)

    clear()

    println("\n" + "=".repeat(60))
    println("EXPLODING KITTENS - Game Administration Console")
    println("=".repeat(60))

    mainMenu(gameRepository, playerRepository, statisticsService, engine)
}

fun mainMenu(
    gameRepository: JsonGameRepository,
    playerRepository: JsonPlayerRepository,
    statisticsService: StatisticsService,
    engine: GameAdminEngine) {
    while (true) {
        println("\nMAIN MENU:")
        println("1. List all players")
        println("2. Show leaderboard")
        println("3. Add new player")
        println("4. List sessions")
        println("5. Create new game session")
        println("6. Resume active game session")
        println("7. Force end game session")

        println("0. Exit")
        print("Choose option: ")

        val input = readLine() ?: break
        val option = input.toIntOrNull()
        clear()

        when (option) {
            1 -> listPlayers(playerRepository)
            2 -> showLeaderboard(statisticsService)
            3 -> addPlayer(playerRepository)
            4 -> listSessions(gameRepository, playerRepository)
            5 -> createSession(engine)
            6 -> resumeGameSession(engine, gameRepository, playerRepository)
            7 -> forceEndGame(engine, gameRepository, playerRepository)
            0 -> {
                exit(gameRepository, playerRepository)
                break
            }
            else -> println("Invalid option. Please try again")
        }
    }
}

fun addPlayer(playerRepository: JsonPlayerRepository) {
    println("\n(Leave empty to exit)")
    print("Enter player name: ")
    val name = readLine() ?: return
    if (name.isEmpty()) {
        return
    }

    val playerStats = PlayerStats(0, 0, 0, 0, 0)
    val player = Player(UUID.randomUUID(), name, false, playerStats)
    playerRepository.addPlayer(player)
    println("Player added: ${player.name} (ID: ${player.id})")
}

fun listPlayers(playerRepository: JsonPlayerRepository) {
    val players = playerRepository.players
    if (players.isEmpty()) {
        println("\nNo players found")
        return
    }
    println("\nPLAYERS LIST:")
    players.entries.forEachIndexed { index, entry ->
        println("${index + 1}. ${entry.value.name} (${entry.key})")
    }
}

fun showLeaderboard(statisticsService: StatisticsService) {
    println("How many users should I display?")
    println("(Leave empty to exit)")
    print("Enter number: ")
    val input = readLine()?.trim()?.toUIntOrNull() ?: return

    val leaderboard = statisticsService.getLeaderboard(input)
    if (leaderboard.isEmpty()) {
        println("No players found")
        return
    }
    println("\nLEADERBOARD:")
    leaderboard.forEachIndexed { index, player ->
        println("${index + 1}. NAME: ${player.name} | ID: ${player.id} | WINS: ${player.stats.wins} | TOTAL GAMES: ${player.stats.totalGames}")
    }
}

fun createSession(
    engine: GameAdminEngine
): UUID? {
    val players = engine.playerRepository.getAllPlayers()

    if (players.size < 2) {
        println("\nNeed 2 players to start a game")
        return null
    }

    println("\nSELECT PLAYERS FOR NEW GAME:")
    players.forEachIndexed { index, player ->
        println("${index + 1}. ${player.name} (${player.id})")
    }

    println("\n(Leave empty to exit)")
    print("Enter first player number: ")
    var input = readLine()?.trim()?.toIntOrNull() ?: return null
    if (input !in 1..players.size) {
        return null
    }
    val player1 = players[input - 1]
    if (player1.isPlaying) {
        println("This player is already playing")
        return null
    }

    print("Enter second player number: ")
    input = readLine()?.trim()?.toIntOrNull() ?: return null
    if (input !in 1..players.size) {
        return null
    }
    val player2 = players[input - 1]
    if (player2.isPlaying) {
        println("This player is already playing")
        return null
    }

    if (player1 == player2) {
        println("You selected already chosen player")
        return null
    }

    println("\nWho will go first?")
    println("1. ${player1.name} (${player1.id})")
    println("2. ${player2.name} (${player2.id})")
    print("Enter player number: ")
    input = readLine()?.trim()?.toIntOrNull() ?: return null

    val sessionId = when (input) {
        1 -> engine.startNewSession(player1.id, player2.id)
        2 -> engine.startNewSession(player2.id, player1.id)
        else -> return null
    }

    val session = engine.gameRepository.getSession(sessionId) ?: return null

    val deckComposition = DeckComposition.CARDS.toMap()

    val availableCards = deckComposition.toMutableMap()

    val hand1 = mutableMapOf(CardType.DEFUSE to 1)
    val hand2 = mutableMapOf(CardType.DEFUSE to 1)
    availableCards[CardType.DEFUSE] = 1

    session.playerHands[player1.id] = hand1
    session.playerHands[player2.id] = hand2

    if (!cardSetupMenu(session, sessionId, player1, player2, hand1, hand2, availableCards, deckComposition, engine)) {
        return null
    }

    val initialPool = availableCards.toMutableMap()

    val drawPile = mutableListOf<CardType>()

    if (!drawPileSetupMenu(session, drawPile, availableCards, initialPool, deckComposition, engine)) {
        engine.gameRepository.removeSession(sessionId)
        return null
    }

    session.drawPile.clear()
    session.drawPile.addAll(drawPile)

    return session.id
}

fun cardSetupMenu(
    session: GameSession,
    sessionId: UUID,
    player1: Player,
    player2: Player,
    hand1: MutableMap<CardType, Int>,
    hand2: MutableMap<CardType, Int>,
    availableCards: MutableMap<CardType, Int>,
    deckComposition: Map<CardType, Int>,
    engine: GameAdminEngine
): Boolean {
    while (true) {
        clear()
        val size1 = hand1.values.sum()
        val size2 = hand2.values.sum()
        val validationResult = engine.ruleValidator.validateCardDistribution(session, deckComposition, availableCards)

        println("CARD SETUP:")
        println("1. ${player1.name} (${size1}/8)")
        println("2. ${player2.name} (${size2}/8)")
        if (validationResult.isValid) {
            println("0. Continue")
        }
        println("Enter. Cancel session")
        print("\nChoose: ")

        val input = readLine()?.trim()
        if (input.isNullOrEmpty()) {
            print("\nCancel session? (Y/N): ")
            val confirm = readLine()?.trim()
            if (confirm.equals("y", ignoreCase = true)) {
                engine.gameRepository.removeSession(sessionId)
                return false
            }
            continue
        }

        val choice = input.toIntOrNull() ?: continue

        when (choice) {
            0 -> {
                if (validationResult.isValid) {
                    return true
                }
                println("\nCannot start:")
                validationResult.errors.forEach { println("  - $it") }
                println("Press Enter to continue...")
                readLine()
            }

            1 -> selectCardsForPlayer(player1, hand1, availableCards)
            2 -> selectCardsForPlayer(player2, hand2, availableCards)
            else -> println("Invalid choice")
        }
    }
}

fun selectCardsForPlayer(
    player: Player,
    hand: MutableMap<CardType, Int>,
    availableCards: MutableMap<CardType, Int>
) {
    while (true) {
        clear()
        val handSize = hand.values.sum()
        println("${player.name.uppercase()} — Select cards (${handSize}/8)")

        println("\nYour hand:")
        if (hand.isEmpty()) {
            println("(empty)")
        } else {
            hand.forEach { (type, count) ->
                val marker = if (type == CardType.DEFUSE) " [locked]" else ""
                println("$type (${count})$marker")
            }
        }

        val selectable = availableCards
            .filter { it.value > 0 && it.key != CardType.EXPLODING_KITTEN }
            .entries.toList()

        println("\nAvailable cards:")
        if (selectable.isEmpty()) {
            println("No cards available!")
        } else {
            selectable.forEachIndexed { index, entry ->
                println("  ${index + 1}. ${entry.key} (${entry.value} left)")
            }
        }

        if (handSize < 8) {
            println("\n0. Remove a card")
            println("Enter. Back to menu")
            print("\nChoose: ")
        } else {
            println("\n0. Remove a card")
            println("Enter. Back to menu (hand is full)")
            print("\nChoose: ")
        }

        val input = readLine()?.trim()
        if (input.isNullOrEmpty()) return

        val choice = input.toIntOrNull() ?: continue

        when {
            choice == 0 -> {
                val removable = hand.filter { (type, count) ->
                    type != CardType.DEFUSE || count > 1
                }
                if (removable.isEmpty()) {
                    println("\nCannot remove any cards (DEFUSE is locked at minimum 1)")
                    println("Press Enter to continue...")
                    readLine()
                    continue
                }
                println("\nSelect card to remove:")
                val removableList = removable.entries.toList()
                removableList.forEachIndexed { index, entry ->
                    val marker = if (entry.key == CardType.DEFUSE) " [min 1]" else ""
                    println("  ${index + 1}. ${entry.key} (${entry.value})$marker")
                }
                print("\nChoose: ")
                val removeInput = readLine()?.trim()?.toIntOrNull()
                if (removeInput != null && removeInput in 1..removableList.size) {
                    val entry = removableList[removeInput - 1]
                    if (entry.value > 1) {
                        hand[entry.key] = entry.value - 1
                    } else {
                        hand.remove(entry.key)
                    }
                    availableCards[entry.key] = (availableCards[entry.key] ?: 0) + 1
                }
            }

            handSize >= 8 -> {
                println("\nHand is full (8/8). Remove a card first.")
                println("Press Enter to continue...")
                readLine()
            }

            choice in 1..selectable.size -> {
                val entry = selectable[choice - 1]
                hand[entry.key] = (hand[entry.key] ?: 0) + 1
                availableCards[entry.key] = entry.value - 1
            }

            else -> {
                println("\nInvalid choice")
                println("Press Enter to continue...")
                readLine()
            }
        }
    }
}

fun drawPileSetupMenu(
    session: GameSession,
    drawPile: MutableList<CardType>,
    availableCards: MutableMap<CardType, Int>,
    initialPool: MutableMap<CardType, Int>,
    deckComposition: Map<CardType, Int>,
    engine: GameAdminEngine
): Boolean {
    fun resetDrawPile() {
        drawPile.forEach { card ->
            availableCards[card] = (availableCards[card] ?: 0) + 1
        }
        drawPile.clear()
        initialPool.forEach { (type, count) ->
            availableCards[type] = count
        }
    }

    while (true) {
        clear()
        val poolTotal = availableCards.values.sum()
        println("DRAW PILE (${drawPile.size} cards):")

        if (drawPile.isEmpty()) {
            println("(empty)")
        } else {
            drawPile.forEachIndexed { index, card ->
                println("  ${index + 1}. $card")
            }
        }

        println("\nPool (${poolTotal} cards remaining):")
        val poolList = availableCards.filter { it.value > 0 }.entries.toList()
        if (poolList.isEmpty()) {
            println("  (empty)")
        } else {
            poolList.forEachIndexed { index, entry ->
                println("  ${index + 1}. ${entry.key} (${entry.value})")
            }
        }

        session.drawPile.clear()
        session.drawPile.addAll(drawPile)
        val validationResult = engine.ruleValidator.validateDrawPile(session, deckComposition, availableCards)

        println("\n0. Reset draw pile")
        if (validationResult.isValid) {
            println("Enter. Continue")
        }
        print("\nChoose: ")

        val input = readLine()?.trim()
        if (input.isNullOrEmpty()) {
            if (validationResult.isValid) {
                clear()
                return true
            }
            continue
        }

        val choice = input.toIntOrNull() ?: continue

        when (choice) {
            0 -> {
                resetDrawPile()
                println("\nDraw pile has been reset!")
            }
            in 1..poolList.size -> {
                val entry = poolList[choice - 1]
                drawPile.add(entry.key)
                availableCards[entry.key] = entry.value - 1
            }
            else -> println("\nInvalid choice")
        }
    }
}

fun exit(gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    if (!gameRepository.saveSessions() || !playerRepository.savePlayers()) {
        println("Failed to save data to JSON")
        print("Do you want to leave without saving data? (Y/N): ")
        val answer = readLine() ?: ""
        if (answer.equals("y", ignoreCase = true)) {
            exitProcess(1)
        } else {
            return
        }
    }
    println("Data has been saved to JSON files")
    return
}

fun listSessions(gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    val sessions = gameRepository.sessions
    if (sessions.isEmpty()) {
        println("\nNo sessions found")
        return
    }
    println("\nSESSIONS LIST:")
    sessions.entries.forEachIndexed { index, entry ->
        val winnerInfo = if (entry.value.status == GameStatus.FINISHED) {
            val winner = entry.value.winnerId?.let { playerRepository.getPlayer(it)?.name } ?: "none"
            " | Winner: $winner"
        } else ""
        println("${index + 1}. STATUS: ${entry.value.status}${winnerInfo} | ID: ${entry.key} | Participants: ${entry.value.participants.map {
            val player = playerRepository.getPlayer(it)
            "${player?.name} ($it)"
        }}")
    }
}

fun forceEndGame(engine: GameAdminEngine, gameRepository: JsonGameRepository, playerRepository: JsonPlayerRepository) {
    val activeSessions = gameRepository.sessions.values.filter { it.status == GameStatus.ACTIVE }

    println("ACTIVE SESSIONS")
    activeSessions.forEachIndexed { index, session ->
        println("${index + 1}. ID: ${session.id} | Participants: ${session.participants.map {
            val player = playerRepository.getPlayer(it)
            "${player?.name} ($it)"
        }}")
    }

    print("Enter session number: ")
    val input = readLine()?.trim()?.toIntOrNull() ?: return
    if (input !in 1..activeSessions.size) {
        return
    }
    val session = activeSessions[input-1]
    engine.endSession(session.id, null)
}

fun resumeGameSession(
    engine: GameAdminEngine,
    gameRepository: JsonGameRepository,
    playerRepository: JsonPlayerRepository
) {
    val activeSessions = gameRepository.sessions.values.filter { it.status == GameStatus.ACTIVE }

    if (activeSessions.isEmpty()) {
        println("\nNo active sessions found")
        return
    }

    println("\nACTIVE SESSIONS:")
    activeSessions.forEachIndexed { index, session ->
        val p1 = playerRepository.getPlayer(session.participants.first())
        val p2 = playerRepository.getPlayer(session.participants.last())
        val turnInfo = session.whoseTurn?.let {
            val currentPlayer = playerRepository.getPlayer(it)
            "Turn: ${currentPlayer?.name}"
        } ?: "Turn: not set"
        println("${index + 1}. ID: ${session.id} | ${p1?.name} vs ${p2?.name} | $turnInfo")
    }

    print("\nChoose session number: ")
    val input = readLine()?.trim()?.toIntOrNull() ?: return
    if (input !in 1..activeSessions.size) {
        return
    }

    val session = activeSessions[input - 1]

    if (session.whoseTurn == null && session.drawPile.isNotEmpty()) {
        println("\nNo current turn is set. Who goes first?")
        session.participants.forEachIndexed { index, pid ->
            val player = playerRepository.getPlayer(pid)
            println("${index + 1}. ${player?.name} ($pid)")
        }
        print("Choose: ")
        val firstInput = readLine()?.trim()?.toIntOrNull()
        if (firstInput != null && firstInput in 1..session.participants.size) {
            session.whoseTurn = session.participants.toList()[firstInput - 1]
        } else {
            return
        }
    }

    playTurns(session, engine, gameRepository, playerRepository)
}

fun playTurns(
    session: GameSession,
    engine: GameAdminEngine,
    gameRepository: JsonGameRepository,
    playerRepository: JsonPlayerRepository
) {
    while (true) {
        clear()

        if (session.status != GameStatus.ACTIVE) {
            println("\nGame session is finished")
            return
        }

        val currentPlayerId = session.whoseTurn
        if (currentPlayerId == null) {
            println("\nNo current player is set")
            return
        }

        val currentPlayer = playerRepository.getPlayer(currentPlayerId)
        val opponentId = session.participants.first { it != currentPlayerId }
        val opponent = playerRepository.getPlayer(opponentId)

        if (currentPlayer == null || opponent == null) {
            println("\nPlayer data not found")
            return
        }

        val topCard = if (session.drawPile.isNotEmpty()) session.drawPile.first() else null

        if (session.mustDefuse) {
            val hand = session.playerHands[currentPlayerId] ?: mutableMapOf()
            if ((hand[CardType.DEFUSE] ?: 0) < 1) {
                println("\n" + "=".repeat(60))
                println("BOOM! ${currentPlayer.name} drew an EXPLODING KITTEN and has no DEFUSE!")
                println("${opponent.name} WINS!")
                println("=".repeat(60))
                engine.endSession(session.id, opponentId)
                gameRepository.saveSessions()
                println("\nPress Enter to continue...")
                readLine()
                return
            }
        }

        println("\nGAME STATE")

        if (session.mustDefuse) {
            println("${currentPlayer.name} MUST DEFUSE")
        } else {
            println("Turn: ${currentPlayer.name} (attack turns: ${session.attackTurnsRemaining})")
        }
        println("Draw pile: ${session.drawPile.size} cards${if (topCard != null) " (top: $topCard)" else ""}")

        println("\n${currentPlayer.name}:")
        val currentHand = session.playerHands[currentPlayerId] ?: mutableMapOf()
        if (currentHand.isEmpty()) {
            println("(empty)")
        } else {
            currentHand.forEach { (type, count) ->
                println("  $type ($count)")
            }
        }

        println("\n${opponent.name}:")
        val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()
        if (opponentHand.isEmpty()) {
            println("(empty)")
        } else {
            opponentHand.forEach { (type, count) ->
                println("  $type ($count)")
            }
        }

        println("\nDiscard pile:")
        if (session.discardPile.isEmpty()) {
            println("(empty)")
        } else {
            session.discardPile.forEach { (type, count) ->
                println("$type ($count)")
            }
        }

        println("\nRecent turns:")
        if (session.turns.isEmpty()) {
            println("(none)")
        } else {
            val recent = session.turns.takeLast(5)
            val startIdx = session.turns.size - recent.size
            recent.forEachIndexed { idx, turn ->
                val turnNum = startIdx + idx + 1
                val turnPlayer = playerRepository.getPlayer(turn.playerId)
                println("  $turnNum. ${turnPlayer?.name}: ${turnDescription(turn)}")
            }
        }

        val actions = buildActions(session, currentPlayerId, opponentId, playerRepository)

        println("\nAVAILABLE ACTIONS")
        actions.forEachIndexed { idx, action ->
            println("${idx + 1}. ${action.label}")
        }
        println("0. Back to main menu")

        print("\nChoose action: ")
        val actionInput = readLine()?.trim()?.toIntOrNull() ?: continue

        if (actionInput == 0) {
            gameRepository.saveSessions()
            return
        }
        if (actionInput !in 1..actions.size) {
            println("Invalid choice")
            println("Press Enter to continue...")
            readLine()
            continue
        }

        val selectedAction = actions[actionInput - 1]
        val turn = collectTurnData(session, selectedAction, currentPlayerId, opponentId)
            ?: run {
                println("Action cancelled")
                println("Press Enter to continue...")
                readLine()
                continue
            }

        val success = engine.addTurn(session.id, turn)
        if (!success) {
            println("\nInvalid move! Turn rejected.")
            println("Press Enter to continue...")
            readLine()
        } else {
            gameRepository.saveSessions()
        }
    }
}

data class ActionOption(
    val type: ActionType,
    val label: String
)

fun buildActions(
    session: GameSession,
    currentPlayerId: UUID,
    opponentId: UUID,
    playerRepository: JsonPlayerRepository
): List<ActionOption> {
    val actions = mutableListOf<ActionOption>()
    val currentHand = session.playerHands[currentPlayerId] ?: mutableMapOf()
    val opponentHand = session.playerHands[opponentId] ?: mutableMapOf()

    if (session.mustDefuse) {
        actions += ActionOption(ActionType.DEFUSE, "Play Defuse (${currentPlayerId.short()})")
        return actions
    }

    val topCard = session.drawPile.firstOrNull()
    if (topCard != null) {
        actions += ActionOption(ActionType.DRAW_CARD, "Draw card: $topCard")
    }

    if ((currentHand[CardType.ATTACK] ?: 0) > 0) {
        actions += ActionOption(ActionType.ATTACK, "Play Attack")
    }

    if ((currentHand[CardType.SKIP] ?: 0) > 0) {
        actions += ActionOption(ActionType.SKIP, "Play Skip")
    }

    if ((currentHand[CardType.SEE_THE_FUTURE] ?: 0) > 0) {
        actions += ActionOption(ActionType.SEE_THE_FUTURE, "Play See the Future")
    }

    if ((currentHand[CardType.SHUFFLE] ?: 0) > 0) {
        actions += ActionOption(ActionType.SHUFFLE, "Play Shuffle")
    }

    if ((currentHand[CardType.FAVOR] ?: 0) > 0 && opponentHand.values.sum() > 0) {
        actions += ActionOption(ActionType.FAVOR, "Play Favor")
    }

    val catCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
        .filter { (currentHand[it] ?: 0) >= 2 }
    for (catCard in catCards) {
        if (opponentHand.values.sum() > 0) {
            actions += ActionOption(ActionType.PLAY_DOUBLE, "Play Double $catCard")
        }
    }

    val tripleCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
        .filter { (currentHand[it] ?: 0) >= 3 }
    for (tripleCard in tripleCards) {
        actions += ActionOption(ActionType.PLAY_TRIPLE, "Play Triple $tripleCard")
    }

    for (pid in session.participants) {
        val hand = session.playerHands[pid] ?: mutableMapOf()
        if ((hand[CardType.NOPE] ?: 0) > 0) {
            val player = playerRepository.getPlayer(pid)
            actions += ActionOption(ActionType.NOPE, "Play Nope (${player?.name})")
        }
    }

    return actions
}

fun collectTurnData(
    session: GameSession,
    action: ActionOption,
    currentPlayerId: UUID,
    opponentId: UUID
): Turn? {
    return when (action.type) {
        ActionType.DRAW_CARD -> {
            val card = session.drawPile.firstOrNull() ?: return null
            Turn.DrawCard(currentPlayerId, card)
        }

        ActionType.DEFUSE -> {
            val maxPos = session.drawPile.size
            println("\nWhere to put the Exploding Kitten? (0 = top, $maxPos = bottom)")
            print("Position (0-$maxPos): ")
            val pos = readLine()?.trim()?.toIntOrNull()
            if (pos == null || pos < 0 || pos > maxPos) return null
            Turn.Defuse(currentPlayerId, pos)
        }

        ActionType.ATTACK -> {
            Turn.Attack(currentPlayerId)
        }

        ActionType.SKIP -> {
            Turn.Skip(currentPlayerId)
        }

        ActionType.SEE_THE_FUTURE -> {
            Turn.SeeTheFuture(currentPlayerId)
        }

        ActionType.SHUFFLE -> {
            val newPile = shuffleDrawPileMenu(session.drawPile.toList()) ?: return null
            Turn.Shuffle(currentPlayerId, newPile)
        }

        ActionType.FAVOR -> {
            val opponentHand = session.playerHands[opponentId] ?: return null
            println("\nChoose a card to take from opponent:")
            val cards = opponentHand.entries.toList()
            cards.forEachIndexed { idx, entry ->
                println("${idx + 1}. ${entry.key} (${entry.value})")
            }
            print("Choose: ")
            val choice = readLine()?.trim()?.toIntOrNull()
            if (choice == null || choice !in 1..cards.size) return null
            Turn.Favor(currentPlayerId, cards[choice - 1].key)
        }

        ActionType.PLAY_DOUBLE -> {
            val hand = session.playerHands[currentPlayerId] ?: return null
            val catCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
                .filter { (hand[it] ?: 0) >= 2 }
            println("\nChoose pair to play:")
            catCards.forEachIndexed { idx, card ->
                println("${idx + 1}. $card")
            }
            print("Choose: ")
            val cardChoice = readLine()?.trim()?.toIntOrNull()
            if (cardChoice == null || cardChoice !in 1..catCards.size) return null
            val pairCard = catCards[cardChoice - 1]

            val opponentHand = session.playerHands[opponentId] ?: return null
            println("\nChoose a card to steal from opponent:")
            val stealCards = opponentHand.entries.toList()
            stealCards.forEachIndexed { idx, entry ->
                println("${idx + 1}. ${entry.key} (${entry.value})")
            }
            print("Choose: ")
            val stealChoice = readLine()?.trim()?.toIntOrNull()
            if (stealChoice == null || stealChoice !in 1..stealCards.size) return null
            Turn.PlayDouble(currentPlayerId, pairCard, stealCards[stealChoice - 1].key)
        }

        ActionType.PLAY_TRIPLE -> {
            val hand = session.playerHands[currentPlayerId] ?: return null
            val tripleCards = listOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
                .filter { (hand[it] ?: 0) >= 3 }
            println("\nChoose triple to play:")
            tripleCards.forEachIndexed { idx, card ->
                println("${idx + 1}. $card")
            }
            print("Choose: ")
            val cardChoice = readLine()?.trim()?.toIntOrNull()
            if (cardChoice == null || cardChoice !in 1..tripleCards.size) return null
            Turn.PlayTriple(currentPlayerId, tripleCards[cardChoice - 1])
        }

        ActionType.NOPE -> {
            val nopePlayerId = action.playerIdFromLabel(session) ?: return null
            println("\nChoose a turn to Nope (index 0-${session.turns.size - 1}):")
            session.turns.forEachIndexed { idx, turn ->
                val isTargetable = turn !is Turn.Defuse && turn !is Turn.DrawCard
                val marker = if (!isTargetable) " [locked]" else ""
                println("  $idx. ${turnDescription(turn)}$marker")
            }
            print("Target turn index: ")
            val targetIdx = readLine()?.trim()?.toIntOrNull()
            if (targetIdx == null || targetIdx !in session.turns.indices) return null
            Turn.Nope(nopePlayerId, targetIdx)
        }
    }
}

fun UUID.short(): String = this.toString().take(8)

fun ActionOption.playerIdFromLabel(session: GameSession): UUID? {
    val match = Regex("\\((.+)\\)").find(this.label)
    return match?.groupValues?.get(1)?.let { str ->
        session.participants.find { it.short() == str || it.toString() == str }
    }
}

fun turnDescription(turn: Turn): String {
    return when (turn) {
        is Turn.DrawCard -> "Drew ${turn.card}"
        is Turn.Defuse -> "Defused (pos ${turn.insertPosition})"
        is Turn.Nope -> "Nope -> turn #${turn.targetTurnIndex}"
        is Turn.Attack -> "Played Attack"
        is Turn.Skip -> "Played Skip"
        is Turn.SeeTheFuture -> "Played See the Future"
        is Turn.Shuffle -> "Played Shuffle"
        is Turn.Favor -> "Played Favor (took ${turn.takenCard})"
        is Turn.Pass -> "Pass"
        is Turn.PlayDouble -> "Double ${turn.card} -> stole ${turn.stolenCard}"
        is Turn.PlayTriple -> "Triple ${turn.card}"
    }
}

fun shuffleDrawPileMenu(currentPile: List<CardType>): List<CardType>? {
    val pool = currentPile.toMutableList()
    val newPile = mutableListOf<CardType>()

    while (true) {
        clear()
        println("SHUFFLE DRAW PILE")
        println("\nNew pile (${newPile.size} cards):")
        if (newPile.isEmpty()) {
            println("(empty)")
        } else {
            newPile.forEachIndexed { idx, card ->
                println("  ${idx + 1}. $card")
            }
        }

        val poolList = pool.groupBy { it }.entries.toList()
        println("\nRemaining pool (${pool.size} cards):")
        if (poolList.isEmpty()) {
            println("(empty)")
        } else {
            poolList.forEachIndexed { idx, entry ->
                println("  ${idx + 1}. ${entry.key} (${entry.value.size})")
            }
        }

        if (pool.isEmpty()) {
            if (newPile.size == currentPile.size) {
                println("\nAll cards placed! Press Enter to confirm")
                readLine()
                return newPile.toList()
            }
        }

        println("\n1-${poolList.size}. Add card from pool to new pile")
        println("0. Reset new pile")
        println("Enter. Cancel")
        print("\nChoose: ")

        val line = readLine()
        if (line.isNullOrEmpty()) return null

        val input = line.trim().toIntOrNull()
        when {
            input == 0 -> {
                newPile.forEach { pool.add(it) }
                newPile.clear()
            }
            input != null && input in 1..poolList.size -> {
                val entry = poolList[input - 1]
                val card = entry.key
                pool.removeFirstCard(card)
                newPile.add(card)
            }
            else -> {
                println("Invalid choice")
                println("Press Enter to continue...")
                readLine()
            }
        }
    }
}

fun MutableList<CardType>.removeFirstCard(card: CardType): Boolean {
    val idx = indexOf(card)
    if (idx >= 0) {
        removeAt(idx)
        return true
    }
    return false
}
