package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.Player
import java.util.UUID

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