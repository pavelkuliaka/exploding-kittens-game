package com.github.pavelkuliaka.integration

import com.github.pavelkuliaka.model.*
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID

class SessionSerializationIntegrationTest {
    private lateinit var gameRepo: JsonGameRepository
    private lateinit var playerRepo: JsonPlayerRepository

    @TempDir
    lateinit var tempDir: Path

    private lateinit var sessionsPath: String
    private lateinit var playersPath: String

    @BeforeEach
    fun setUp() {
        sessionsPath = File(tempDir.toFile(), "sessions.json").absolutePath
        playersPath = File(tempDir.toFile(), "players.json").absolutePath
        gameRepo = JsonGameRepository(sessionsPath)
        playerRepo = JsonPlayerRepository(playersPath)
    }

    @Test
    fun `simple session with hands and drawPile roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3),
            status = GameStatus.ACTIVE,
            whoseTurn = p1,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.SKIP to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1, CardType.FAVOR to 1)
            )
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertNotNull(loaded)
        assertEquals(GameStatus.ACTIVE, loaded?.status)
        assertEquals(p1, loaded?.whoseTurn)
        assertEquals(3, loaded?.drawPile?.size)
        assertEquals(1, loaded?.playerHands?.get(p1)?.get(CardType.DEFUSE))
        assertEquals(1, loaded?.playerHands?.get(p1)?.get(CardType.ATTACK))
        assertEquals(1, loaded?.playerHands?.get(p2)?.get(CardType.NOPE))
    }

    @Test
    fun `discardPile counts preserved after roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(
                CardType.ATTACK to 1,
                CardType.SKIP to 2,
                CardType.SEE_THE_FUTURE to 1,
                CardType.NOPE to 3
            ),
            drawPile = mutableListOf(CardType.SPECIAL_1),
            status = GameStatus.ACTIVE,
            whoseTurn = p1,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1)
            )
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertEquals(1, loaded?.discardPile?.get(CardType.ATTACK))
        assertEquals(2, loaded?.discardPile?.get(CardType.SKIP))
        assertEquals(1, loaded?.discardPile?.get(CardType.SEE_THE_FUTURE))
        assertEquals(3, loaded?.discardPile?.get(CardType.NOPE))
    }

    @Test
    fun `empty drawPile and discardPile handled correctly`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.ACTIVE,
            whoseTurn = p2,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1)
            )
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertNotNull(loaded)
        assertTrue(loaded!!.drawPile.isEmpty())
        assertTrue(loaded.discardPile.isEmpty())
    }

    @Test
    fun `finished session with winnerId roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.FINISHED,
            whoseTurn = p1,
            playerHands = mutableMapOf(),
            winnerId = p1
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertEquals(GameStatus.FINISHED, loaded?.status)
        assertEquals(p1, loaded?.winnerId)
    }

    @Test
    fun `session with attackTurnsRemaining and mustDefuse roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(CardType.EXPLODING_KITTEN),
            status = GameStatus.ACTIVE,
            whoseTurn = p2,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1)
            ),
            attackTurnsRemaining = 2,
            mustDefuse = true
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertEquals(2, loaded?.attackTurnsRemaining)
        assertTrue(loaded?.mustDefuse == true)
        assertEquals(p2, loaded?.whoseTurn)
    }

    @Test
    fun `multiple sessions survive roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val p3 = UUID.randomUUID()

        val s1 = GameSession(
            id = UUID.randomUUID(), participants = setOf(p1, p2),
            turns = mutableListOf(), discardPile = mutableMapOf(),
            drawPile = mutableListOf(), status = GameStatus.FINISHED,
            whoseTurn = p1, playerHands = mutableMapOf(), winnerId = p1
        )
        val s2 = GameSession(
            id = UUID.randomUUID(), participants = setOf(p2, p3),
            turns = mutableListOf(), discardPile = mutableMapOf(),
            drawPile = mutableListOf(CardType.SPECIAL_1), status = GameStatus.ACTIVE,
            whoseTurn = p3, playerHands = mutableMapOf()
        )
        gameRepo.addSession(s1)
        gameRepo.addSession(s2)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        assertEquals(2, newRepo.sessions.size)
        val loadedS1 = newRepo.getSession(s1.id)
        val loadedS2 = newRepo.getSession(s2.id)
        assertEquals(GameStatus.FINISHED, loadedS1?.status)
        assertEquals(GameStatus.ACTIVE, loadedS2?.status)
    }

    @Test
    fun `null whoseTurn preserved after roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = mutableListOf(),
            status = GameStatus.FINISHED,
            whoseTurn = null,
            playerHands = mutableMapOf(),
            winnerId = p1
        )
        gameRepo.addSession(session)
        assertTrue(gameRepo.saveSessions())

        val newRepo = JsonGameRepository(sessionsPath)
        assertTrue(newRepo.loadSessions())

        val loaded = newRepo.getSession(session.id)
        assertNull(loaded?.whoseTurn)
    }
}
