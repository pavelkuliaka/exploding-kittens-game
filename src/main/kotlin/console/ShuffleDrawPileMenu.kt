package com.github.pavelkuliaka.console

import com.github.pavelkuliaka.model.CardType

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