package com.github.pavelkuliaka.integration

import TestFixtures
import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.gui.TestGameRepository
import com.github.pavelkuliaka.gui.TestPlayerRepository
import com.github.pavelkuliaka.model.*
import com.github.pavelkuliaka.repository.IGameRepository
import com.github.pavelkuliaka.repository.IPlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class NopeChainIntegrationTest {
    private lateinit var engine: GameAdminEngine
    private lateinit var gameRepo: IGameRepository
    private lateinit var playerRepo: IPlayerRepository
    private lateinit var statsService: StatisticsService

    @BeforeEach
    fun setUp() {
        gameRepo = TestGameRepository()
        playerRepo = TestPlayerRepository()
        val validator = RuleValidator()
        statsService = StatisticsService(playerRepo)
        engine = GameAdminEngine(
            gameRepo, playerRepo, validator, statsService
        )
    }

    @Test
    fun `single Nope on Attack cancels attack`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertEquals(p1, session?.whoseTurn)
        assertEquals(0, session?.attackTurnsRemaining)
    }

    @Test
    fun `two Nope on Attack restores original effect`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        engine.addTurn(sessionId, Turn.Nope(p1, 1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(3, session?.turns?.size)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(2, session?.attackTurnsRemaining)
    }

    @Test
    fun `Nope on Favor reverses card transfer`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1, CardType.NOPE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Favor(p1, CardType.SKIP))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertNull(session?.playerHands?.get(p1)?.get(CardType.SKIP))
        assertEquals(1, session?.playerHands?.get(p2)?.get(CardType.SKIP))
    }

    @Test
    fun `cards spent even when Nope cancels effect`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 2, CardType.NOPE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Attack(p2))
        engine.addTurn(sessionId, Turn.Nope(p1, 1))
        val session = gameRepo.getSession(sessionId)
        assertNull(session?.playerHands?.get(p2)?.get(CardType.ATTACK))
        assertEquals(2, session?.discardPile?.get(CardType.ATTACK))
        assertEquals(1, session?.discardPile?.get(CardType.NOPE))
        assertEquals(p2, session?.whoseTurn)
    }

    @Test
    fun `quadruple Nope on Attack`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 3),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        engine.addTurn(sessionId, Turn.Nope(p1, 1))
        engine.addTurn(sessionId, Turn.Nope(p2, 2))
        engine.addTurn(sessionId, Turn.Nope(p1, 3))
        val session = gameRepo.getSession(sessionId)
        assertEquals(5, session?.turns?.size)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(2, session?.attackTurnsRemaining)
    }

    @Test
    fun `Nope on SeeTheFuture`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SEE_THE_FUTURE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.SeeTheFuture(p1))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertEquals(2, session?.turns?.size)
        assertEquals(p1, session?.whoseTurn)
        assertEquals(1, session?.discardPile?.get(CardType.SEE_THE_FUTURE))
    }

    @Test
    fun `Nope on PlayDouble reverses steal`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1, CardType.SKIP to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.SKIP))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertNull(session?.playerHands?.get(p1)?.get(CardType.SKIP))
        assertEquals(1, session?.playerHands?.get(p2)?.get(CardType.SKIP))
        assertEquals(2, session?.discardPile?.get(CardType.SPECIAL_1))
    }

    @Test
    fun `Nope on Shuffle reverses drawPile change`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            drawPile = mutableListOf(CardType.SKIP, CardType.ATTACK, CardType.NOPE)
        )
        val newOrder = listOf(CardType.NOPE, CardType.SKIP, CardType.ATTACK)
        engine.addTurn(sessionId, Turn.Shuffle(p1, newOrder))
        engine.addTurn(sessionId, Turn.Nope(p2, 0))
        val session = gameRepo.getSession(sessionId)
        assertEquals(listOf(CardType.SKIP, CardType.ATTACK, CardType.NOPE), session?.drawPile)
        assertEquals(1, session?.discardPile?.get(CardType.SHUFFLE))
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
