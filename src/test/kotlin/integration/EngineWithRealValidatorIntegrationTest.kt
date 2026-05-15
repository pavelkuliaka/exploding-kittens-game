package com.github.pavelkuliaka.integration

import TestFixtures
import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.*
import com.github.pavelkuliaka.repository.JsonGameRepository
import com.github.pavelkuliaka.repository.JsonPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID

class EngineWithRealValidatorIntegrationTest {
    private lateinit var engine: GameAdminEngine
    private lateinit var gameRepo: JsonGameRepository
    private lateinit var playerRepo: JsonPlayerRepository
    private lateinit var statsService: StatisticsService

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
        val validator = RuleValidator()
        statsService = StatisticsService(playerRepo)
        engine = GameAdminEngine(gameRepo, playerRepo, validator, statsService)
    }

    @Test
    fun `Attack without ATTACK card is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p1)))
    }

    @Test
    fun `Defuse without mustDefuse is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Defuse(p1, 0)))
    }

    @Test
    fun `Defuse with negative insertPosition is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Defuse(p1, -1)))
    }

    @Test
    fun `Nope targeting Defuse is rejected`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        engine.addTurn(sessionId, Turn.Defuse(p1, 0))
        assertFalse(engine.addTurn(sessionId, Turn.Nope(p2, 0)))
    }

    @Test
    fun `Nope targeting DrawCard is rejected`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            drawPile = mutableListOf(CardType.EXPLODING_KITTEN)
        )
        engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.EXPLODING_KITTEN))
        assertFalse(engine.addTurn(sessionId, Turn.Nope(p2, 0)))
    }

    @Test
    fun `Wrong player turn is rejected`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p2)))
    }

    @Test
    fun `Attack followed by Nope cancels effect`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertEquals(p1, session?.whoseTurn)
        assertEquals(0, session?.attackTurnsRemaining)
        assertEquals(2, session?.turns?.size)
    }

    @Test
    fun `Shuffle with wrong composition is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SKIP, CardType.ATTACK, CardType.NOPE)
        )
        val wrongOrder = listOf(CardType.SKIP, CardType.NOPE, CardType.SKIP)
        assertFalse(engine.addTurn(sessionId, Turn.Shuffle(p1, wrongOrder)))
    }

    @Test
    fun `Shuffle with valid composition succeeds`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SKIP, CardType.ATTACK, CardType.NOPE)
        )
        val newOrder = listOf(CardType.NOPE, CardType.SKIP, CardType.ATTACK)
        assertTrue(engine.addTurn(sessionId, Turn.Shuffle(p1, newOrder)))
        assertEquals(newOrder, gameRepo.getSession(sessionId)?.drawPile)
    }

    @Test
    fun `Favor when opponent lacks card is rejected`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Favor(p1, CardType.NOPE)))
    }

    @Test
    fun `Favor successfully takes card`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            drawPile = mutableListOf()
        )
        assertTrue(engine.addTurn(sessionId, Turn.Favor(p1, CardType.SKIP)))
        val session = gameRepo.getSession(sessionId)
        assertNull(session?.playerHands?.get(p2)?.get(CardType.SKIP))
        assertEquals(1, session?.playerHands?.get(p1)?.get(CardType.SKIP))
    }

    @Test
    fun `Pass always returns false`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Pass(p1)))
    }

    @Test
    fun `PlayDouble without 2 matching cards is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.SKIP)))
    }

    @Test
    fun `PlayTriple without 3 matching cards is rejected`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_2 to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.PlayTriple(p1, CardType.SPECIAL_2)))
    }

    @Test
    fun `Finished session rejects all turns`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        engine.endSession(sessionId, p1)
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p1)))
    }

    @Test
    fun `Defuse successfully places kitten and switches turn`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.EXPLODING_KITTEN),
            mustDefuse = true
        )
        assertTrue(engine.addTurn(sessionId, Turn.Defuse(p1, 0)))
        val session = gameRepo.getSession(sessionId)
        assertFalse(session?.mustDefuse == true)
        assertEquals(p2, session?.whoseTurn)
    }

    @Test
    fun `DrawCard normal card switches turn`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SPECIAL_1)
        )
        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SPECIAL_1)))
        val session = gameRepo.getSession(sessionId)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(1, session?.playerHands?.get(p1)?.get(CardType.SPECIAL_1))
        assertTrue(session?.drawPile?.isEmpty() == true)
    }

    @Test
    fun `SeeTheFuture card is spent`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SEE_THE_FUTURE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertTrue(engine.addTurn(sessionId, Turn.SeeTheFuture(p1)))
        val session = gameRepo.getSession(sessionId)
        assertNull(session?.playerHands?.get(p1)?.get(CardType.SEE_THE_FUTURE))
        assertEquals(1, session?.discardPile?.get(CardType.SEE_THE_FUTURE))
    }

    private fun createSessionWithHands(
        p1Id: UUID = UUID.randomUUID(),
        p2Id: UUID = UUID.randomUUID(),
        p1Hand: MutableMap<CardType, Int>,
        p2Hand: MutableMap<CardType, Int>,
        drawPile: MutableList<CardType>,
        mustDefuse: Boolean = false
    ): Triple<UUID, UUID, UUID> {
        playerRepo.addPlayer(TestFixtures.createPlayer(p1Id, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2Id, "P2"))
        val sessionId = engine.startNewSession(p1Id, p2Id)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1Id] = p1Hand
        session.playerHands[p2Id] = p2Hand
        session.drawPile.clear()
        session.drawPile.addAll(drawPile)
        session.mustDefuse = mustDefuse
        return Triple(sessionId, p1Id, p2Id)
    }
}
