package com.github.pavelkuliaka.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class ModelsTest {

    @Test
    fun `snapshot captures all fields`() {
        val playerId = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(playerId),
            turns = mutableListOf(),
            discardPile = mutableMapOf(CardType.SKIP to 1),
            drawPile = mutableListOf(CardType.ATTACK, CardType.NOPE),
            status = GameStatus.ACTIVE,
            whoseTurn = playerId,
            playerHands = mutableMapOf(playerId to mutableMapOf(CardType.DEFUSE to 1)),
            attackTurnsRemaining = 2,
            mustDefuse = true,
            winnerId = null
        )

        val snapshot = session.snapshot()

        assertEquals(playerId, snapshot.whoseTurn)
        assertEquals(2, snapshot.attackTurnsRemaining)
        assertTrue(snapshot.mustDefuse)
        assertEquals(1, snapshot.playerHands[playerId]?.get(CardType.DEFUSE))
        assertEquals(1, snapshot.discardPile[CardType.SKIP])
        assertEquals(CardType.ATTACK, snapshot.drawPile[0])
        assertEquals(CardType.NOPE, snapshot.drawPile[1])
        assertNull(snapshot.winnerId)
    }

    @Test
    fun `snapshot with winnerId captures it`() {
        val playerId = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(playerId),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.FINISHED,
            whoseTurn = null,
            winnerId = playerId
        )

        val snapshot = session.snapshot()

        assertEquals(playerId, snapshot.winnerId)
    }

    @Test
    fun `restoreSnapshot restores all fields`() {
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(UUID.randomUUID()),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.ACTIVE,
            whoseTurn = null
        )

        val playerId = UUID.randomUUID()
        val originalSnapshot = GameStateSnapshot(
            whoseTurn = playerId,
            attackTurnsRemaining = 3,
            mustDefuse = true,
            playerHands = mapOf(playerId to mapOf(CardType.DEFUSE to 2)),
            discardPile = mapOf(CardType.SKIP to 1),
            drawPile = listOf(CardType.ATTACK, CardType.NOPE),
            winnerId = null
        )

        session.restoreSnapshot(originalSnapshot)

        assertEquals(playerId, session.whoseTurn)
        assertEquals(3, session.attackTurnsRemaining)
        assertTrue(session.mustDefuse)
        assertEquals(2, session.playerHands[playerId]?.get(CardType.DEFUSE))
        assertEquals(1, session.discardPile[CardType.SKIP])
        assertEquals(2, session.drawPile.size)
        assertEquals(CardType.ATTACK, session.drawPile[0])
    }

    @Test
    fun `restoreSnapshot does not mutate original snapshot`() {
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(UUID.randomUUID()),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.ACTIVE,
            whoseTurn = null
        )

        val playerId = UUID.randomUUID()
        val snapshot = GameStateSnapshot(
            whoseTurn = playerId,
            attackTurnsRemaining = 0,
            mustDefuse = false,
            playerHands = mapOf(playerId to mapOf(CardType.DEFUSE to 1)),
            discardPile = mapOf(),
            drawPile = listOf(CardType.ATTACK),
            winnerId = null
        )

        session.restoreSnapshot(snapshot)

        session.whoseTurn = null
        session.attackTurnsRemaining = 10
        session.mustDefuse = true

        assertEquals(playerId, snapshot.whoseTurn)
        assertEquals(0, snapshot.attackTurnsRemaining)
        assertFalse(snapshot.mustDefuse)
    }
}
