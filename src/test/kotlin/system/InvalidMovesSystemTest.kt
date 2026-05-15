package system

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

class InvalidMovesSystemTest {
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
    fun `DrawCard when not your turn`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SPECIAL_1)
        )
        assertFalse(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.SPECIAL_1)))
    }

    @Test
    fun `DrawCard when mustDefuse is true`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SPECIAL_1, CardType.EXPLODING_KITTEN),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SPECIAL_1)))
    }

    @Test
    fun `Attack when mustDefuse is true`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p1)))
    }

    @Test
    fun `Skip when not your turn`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Skip(p2)))
    }

    @Test
    fun `SeeTheFuture without card`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.SeeTheFuture(p1)))
    }

    @Test
    fun `SeeTheFuture when mustDefuse is true`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2, CardType.SEE_THE_FUTURE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.SeeTheFuture(p1)))
    }

    @Test
    fun `Favor when opponent has no cards`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Favor(p1, CardType.SKIP)))
    }

    @Test
    fun `PlayDouble when opponent has no stealable cards`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.SKIP)))
    }

    @Test
    fun `PlayTriple without 3 matching cat cards`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_2 to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.PlayTriple(p1, CardType.SPECIAL_2)))
    }

    @Test
    fun `Nope with out of bounds targetTurnIndex`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.Nope(p2, 0)))
    }

    @Test
    fun `Nope with negative targetTurnIndex`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        assertFalse(engine.addTurn(sessionId, Turn.Nope(p2, -1)))
    }

    @Test
    fun `DrawCard with wrong top card`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SPECIAL_1, CardType.SPECIAL_2)
        )
        assertFalse(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SPECIAL_2)))
    }

    @Test
    fun `DrawCard with empty drawPile`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        assertFalse(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SPECIAL_1)))
    }

    @Test
    fun `Defuse without DEFUSE card in hand`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Defuse(p1, 0)))
    }

    @Test
    fun `Defuse when not current player`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 2),
            drawPile = mutableListOf(CardType.EXPLODING_KITTEN),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Defuse(p2, 0)))
    }

    @Test
    fun `Skip when mustDefuse is true`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2, CardType.SKIP to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Skip(p1)))
    }

    @Test
    fun `non-participant cannot make any move`() {
        val (sessionId, _, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        val nonParticipant = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(nonParticipant, "Ghost"))
        assertFalse(engine.addTurn(sessionId, Turn.Attack(nonParticipant)))
    }

    @Test
    fun `state unchanged after rejected turn`() {
        val (sessionId, _, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            drawPile = mutableListOf(CardType.SPECIAL_1)
        )
        val stateBefore = gameRepo.getSession(sessionId)!!.copy(
            turns = gameRepo.getSession(sessionId)!!.turns.toMutableList(),
            discardPile = gameRepo.getSession(sessionId)!!.discardPile.toMutableMap(),
            playerHands = gameRepo.getSession(sessionId)!!.playerHands.mapValues { it.value.toMutableMap() }.toMutableMap(),
            drawPile = gameRepo.getSession(sessionId)!!.drawPile.toMutableList()
        )

        engine.addTurn(sessionId, Turn.Attack(p2))

        val stateAfter = gameRepo.getSession(sessionId)!!
        assertEquals(stateBefore.whoseTurn, stateAfter.whoseTurn)
        assertEquals(stateBefore.turns.size, stateAfter.turns.size)
        assertEquals(stateBefore.drawPile, stateAfter.drawPile)
        assertEquals(stateBefore.discardPile, stateAfter.discardPile)
    }

    @Test
    fun `Attack rejected when mustDefuse is true and no card`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p1)))
    }

    @Test
    fun `Favor when mustDefuse is true`() {
        val (sessionId, p1) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 2, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        assertFalse(engine.addTurn(sessionId, Turn.Favor(p1, CardType.SKIP)))
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
