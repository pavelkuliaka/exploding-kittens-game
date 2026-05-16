package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

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