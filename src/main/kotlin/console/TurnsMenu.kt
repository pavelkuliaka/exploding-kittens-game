package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.ActionType
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import java.util.UUID

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

fun ActionOption.playerIdFromLabel(session: GameSession): UUID? {
    val match = Regex("\\((.+)\\)").find(this.label)
    return match?.groupValues?.get(1)?.let { str ->
        session.participants.find { it.short() == str || it.toString() == str }
    }
}

fun UUID.short(): String = this.toString().take(8)

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