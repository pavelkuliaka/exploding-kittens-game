package com.github.pavelkuliaka.engine

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.repository.IGameRepository
import com.github.pavelkuliaka.repository.IPlayerRepository
import com.github.pavelkuliaka.service.IStatisticsService
import com.github.pavelkuliaka.validation.IRuleValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GameAdminEngineTest {
    private lateinit var gameRepo: TestGameRepo
    private lateinit var playerRepo: TestPlayerRepo
    private lateinit var engine: GameAdminEngine

    @BeforeEach
    fun setUp() {
        gameRepo = TestGameRepo()
        playerRepo = TestPlayerRepo()
        val validator = TestRuleValidator()
        val statsService = TestStatsService()
        engine = GameAdminEngine(
            gameRepo,
            playerRepo,
            validator,
            statsService
        )
    }

    @Test
    fun `addTurn returns false for non-existent session`() {
        val result = engine.addTurn(UUID.randomUUID(), Turn.Attack(UUID.randomUUID()))
        assertFalse(result)
    }

    @Test
    fun `addTurn saves initialState on first turn`() {
        val (sessionId, p1) = createBasicSession()
        val turn = Turn.Attack(p1)
        engine.addTurn(sessionId, turn)
        val session = gameRepo.getSession(sessionId)
        assertNotNull(session?.initialState)
    }

    @Test
    fun `addTurn DrawCard normal card switches turn`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SKIP, CardType.NOPE)
        )
        engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SKIP))
        val session = gameRepo.getSession(sessionId)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(1, session?.playerHands?.get(p1)?.get(CardType.SKIP))
        assertEquals(CardType.NOPE, session?.drawPile?.firstOrNull())
    }

    @Test
    fun `addTurn DrawCard EXPLODING_KITTEN sets mustDefuse`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.EXPLODING_KITTEN, CardType.NOPE)
        )
        engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.EXPLODING_KITTEN))
        val session = gameRepo.getSession(sessionId)
        assertTrue(session?.mustDefuse == true)
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn Defuse resolves mustDefuse`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            mustDefuse = true
        )
        engine.addTurn(sessionId, Turn.Defuse(p1, 0))
        val session = gameRepo.getSession(sessionId)
        assertFalse(session?.mustDefuse == true)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(0, session?.playerHands?.get(p1)?.get(CardType.DEFUSE) ?: 0)
        assertEquals(1, session?.discardPile?.get(CardType.DEFUSE))
        assertEquals(CardType.EXPLODING_KITTEN, session?.drawPile?.firstOrNull())
    }

    @Test
    fun `addTurn Attack gives opponent 2 turns`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(2, session?.attackTurnsRemaining)
        assertEquals(p2, session?.whoseTurn)
        assertEquals(1, session?.discardPile?.get(CardType.ATTACK))
    }

    @Test
    fun `addTurn Skip decrements attackTurnsRemaining`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            attackTurnsRemaining = 2
        )
        engine.addTurn(sessionId, Turn.Skip(p1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(1, session?.attackTurnsRemaining)
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn Skip with 1 attackTurn switches turn`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SKIP to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            attackTurnsRemaining = 1
        )
        engine.addTurn(sessionId, Turn.Skip(p1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(0, session?.attackTurnsRemaining)
        assertEquals(p2, session?.whoseTurn)
    }

    @Test
    fun `addTurn SeeTheFuture does not change turn`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SEE_THE_FUTURE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.SeeTheFuture(p1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(p1, session?.whoseTurn)
        assertEquals(1, session?.discardPile?.get(CardType.SEE_THE_FUTURE))
        assertEquals(0, session?.playerHands?.get(p1)?.get(CardType.SEE_THE_FUTURE) ?: 0)
    }

    @Test
    fun `addTurn Shuffle replaces drawPile`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.ATTACK, CardType.SKIP)
        )
        val newPile = listOf(CardType.SKIP, CardType.ATTACK)
        engine.addTurn(sessionId, Turn.Shuffle(p1, newPile))
        val session = gameRepo.getSession(sessionId)
        assertEquals(2, session?.drawPile?.size)
        assertEquals(CardType.SKIP, session?.drawPile?.get(0))
        assertEquals(CardType.ATTACK, session?.drawPile?.get(1))
        assertEquals(1, session?.discardPile?.get(CardType.SHUFFLE))
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn Favor transfers card from opponent`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Favor(p1, CardType.ATTACK))
        val session = gameRepo.getSession(sessionId)
        assertEquals(1, session?.playerHands?.get(p2)?.get(CardType.ATTACK))
        assertEquals(1, session?.playerHands?.get(p1)?.get(CardType.ATTACK))
        assertEquals(1, session?.discardPile?.get(CardType.FAVOR))
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn PlayDouble transfers stolenCard`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.ATTACK))
        val session = gameRepo.getSession(sessionId)
        assertEquals(0, session?.playerHands?.get(p2)?.get(CardType.ATTACK) ?: 0)
        assertEquals(1, session?.playerHands?.get(p1)?.get(CardType.ATTACK))
        assertEquals(2, session?.discardPile?.get(CardType.SPECIAL_1))
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn PlayTriple discards 3 cards`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.SPECIAL_2 to 3),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.PlayTriple(p1, CardType.SPECIAL_2))
        val session = gameRepo.getSession(sessionId)
        assertEquals(0, session?.playerHands?.get(p1)?.get(CardType.SPECIAL_2) ?: 0)
        assertEquals(3, session?.discardPile?.get(CardType.SPECIAL_2))
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn Nope discards NOPE card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val (sessionId) = createSessionWithHands(
            p1Id = p1, p2Id = p2,
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.NOPE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(),
            turns = mutableListOf(Turn.Attack(p1))
        )
        engine.addTurn(sessionId, Turn.Nope(p1, 0))
        val session = gameRepo.getSession(sessionId)
        assertEquals(0, session?.playerHands?.get(p1)?.get(CardType.NOPE) ?: 0)
        assertEquals(1, session?.discardPile?.get(CardType.NOPE))
    }

    @Test
    fun `Nope chain cancels Attack effect`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val (sessionId) = createSessionWithHands(
            p1Id = p1, p2Id = p2,
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 2, CardType.NOPE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Attack(p2))
        engine.addTurn(sessionId, Turn.Nope(p1, 1))
        val session = gameRepo.getSession(sessionId)
        assertEquals(2, session?.attackTurnsRemaining)
        assertEquals(p2, session?.whoseTurn)
    }

    @Test
    fun `Nope on Nope restores original effect`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val (sessionId) = createSessionWithHands(
            p1Id = p1, p2Id = p2,
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 2, CardType.NOPE to 2),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.NOPE to 2),
            drawPile = mutableListOf()
        )
        engine.addTurn(sessionId, Turn.Attack(p1))
        engine.addTurn(sessionId, Turn.Attack(p2))
        engine.addTurn(sessionId, Turn.Nope(p1, 1))
        engine.addTurn(sessionId, Turn.Nope(p2, 2))
        val session = gameRepo.getSession(sessionId)
        assertNotNull(session)
        assertEquals(4, session?.turns?.size)
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `startNewSession creates session with correct state`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val player1 = Player(
            p1,
            "P1",
            false,
            PlayerStats(0, 0, 0, 0, 0)
        )
        val player2 = Player(
            p2,
            "P2",
            false,
            PlayerStats(0, 0, 0, 0, 0)
        )
        playerRepo.addPlayer(player1)
        playerRepo.addPlayer(player2)

        val sessionId = engine.startNewSession(p1, p2)

        val session = gameRepo.getSession(sessionId)
        assertNotNull(session)
        assertEquals(setOf(p1, p2), session?.participants)
        assertEquals(p1, session?.whoseTurn)
        assertEquals(GameStatus.ACTIVE, session?.status)
        assertTrue(playerRepo.getPlayer(p1)?.isPlaying == true)
        assertTrue(playerRepo.getPlayer(p2)?.isPlaying == true)
    }

    @Test
    fun `endSession sets FINISHED and winnerId`() {
        val (sessionId, p1, _) = createBasicSession()
        engine.endSession(sessionId, p1)
        val session = gameRepo.getSession(sessionId)
        assertEquals(GameStatus.FINISHED, session?.status)
        assertEquals(p1, session?.winnerId)
    }

    @Test
    fun `endSession with null winnerId leaves winnerId null`() {
        val (sessionId, _, _) = createBasicSession()
        engine.endSession(sessionId, null)
        val session = gameRepo.getSession(sessionId)
        assertEquals(GameStatus.FINISHED, session?.status)
        assertNull(session?.winnerId)
    }

    @Test
    fun `addTurn DrawCard with attackTurnsRemaining decrements counter`() {
        val (sessionId, p1, _) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SKIP, CardType.NOPE),
            attackTurnsRemaining = 2
        )
        engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SKIP))
        val session = gameRepo.getSession(sessionId)
        assertEquals(1, session?.attackTurnsRemaining)
        assertEquals(p1, session?.whoseTurn)
    }

    @Test
    fun `addTurn DrawCard with attackTurnsRemaining 1 switches turn`() {
        val (sessionId, p1, p2) = createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf(CardType.SKIP),
            attackTurnsRemaining = 1
        )
        engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.SKIP))
        val session = gameRepo.getSession(sessionId)
        assertEquals(0, session?.attackTurnsRemaining)
        assertEquals(p2, session?.whoseTurn)
    }

    private fun createBasicSession(): Triple<UUID, UUID, UUID> {
        return createSessionWithHands(
            p1Hand = mutableMapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1),
            p2Hand = mutableMapOf(CardType.DEFUSE to 1),
            drawPile = mutableListOf()
        )
    }

    private fun createSessionWithHands(
        p1Id: UUID = UUID.randomUUID(),
        p2Id: UUID = UUID.randomUUID(),
        p1Hand: MutableMap<CardType, Int>,
        p2Hand: MutableMap<CardType, Int>,
        drawPile: MutableList<CardType>,
        mustDefuse: Boolean = false,
        attackTurnsRemaining: Int = 0,
        turns: MutableList<Turn> = mutableListOf()
    ): Triple<UUID, UUID, UUID> {
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1Id, p2Id),
            turns = turns,
            discardPile = mutableMapOf(),
            drawPile = drawPile,
            status = GameStatus.ACTIVE,
            whoseTurn = p1Id,
            playerHands = mutableMapOf(p1Id to p1Hand, p2Id to p2Hand),
            mustDefuse = mustDefuse,
            attackTurnsRemaining = attackTurnsRemaining
        )
        gameRepo.addSession(session)
        return Triple(session.id, p1Id, p2Id)
    }
}

private class TestGameRepo : IGameRepository {
    private val sessions = mutableMapOf<UUID, GameSession>()
    override fun addSession(gameSession: GameSession) { sessions[gameSession.id] = gameSession }
    override fun getSession(sessionId: UUID): GameSession? = sessions[sessionId]
    override fun removeSession(sessionId: UUID) { sessions.remove(sessionId) }
    override fun getAllSessions(): List<GameSession> = sessions.values.toList()
}

private class TestPlayerRepo : IPlayerRepository {
    private val players = mutableMapOf<UUID, Player>()
    override fun addPlayer(player: Player) { players[player.id] = player }
    override fun getPlayer(playerId: UUID): Player? = players[playerId]
    override fun removePlayer(playerId: UUID) { players.remove(playerId) }
    override fun getAllPlayers(): List<Player> = players.values.toList()
}

private class TestRuleValidator : IRuleValidator {
    override fun validateCardDistribution(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ) = TODO()
    override fun validateDrawPile(
        session: GameSession,
        deckComposition: Map<CardType, Int>,
        availableCards: Map<CardType, Int>
    ) = TODO()
    override fun validateTurn(gameSession: GameSession, nextTurn: Turn): Boolean {
        if (gameSession.status != GameStatus.ACTIVE) return false
        if (nextTurn.playerId !in gameSession.participants) return false
        if (nextTurn.playerId !in gameSession.playerHands) return false
        val hand = gameSession.playerHands[nextTurn.playerId] ?: return false

        return when (nextTurn) {
            is Turn.Nope -> {
                if (nextTurn.targetTurnIndex < 0 || nextTurn.targetTurnIndex >= gameSession.turns.size) return false
                val targetTurn = gameSession.turns[nextTurn.targetTurnIndex]
                if (targetTurn is Turn.Defuse) return false
                if (targetTurn is Turn.DrawCard) return false
                (hand[CardType.NOPE] ?: 0) > 0
            }
            is Turn.Defuse -> {
                if (!gameSession.mustDefuse) return false
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                (hand[CardType.DEFUSE] ?: 0) > 0
            }
            is Turn.DrawCard -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (gameSession.mustDefuse) return false
                if (gameSession.drawPile.isEmpty()) return false
                if (gameSession.drawPile.first() != nextTurn.card) return false
                true
            }
            is Turn.Attack -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                (hand[CardType.ATTACK] ?: 0) > 0
            }
            is Turn.Skip -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                (hand[CardType.SKIP] ?: 0) > 0
            }
            is Turn.SeeTheFuture -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                (hand[CardType.SEE_THE_FUTURE] ?: 0) > 0
            }
            is Turn.Shuffle -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if ((hand[CardType.SHUFFLE] ?: 0) < 1) return false
                if (nextTurn.newDrawPile.size != gameSession.drawPile.size) return false
                if (
                    nextTurn.newDrawPile.groupBy { it }.mapValues { it.value.size } !=
                    gameSession.drawPile.groupBy { it }.mapValues { it.value.size }
                    ) return false
                true
            }
            is Turn.Favor -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if ((hand[CardType.FAVOR] ?: 0) < 1) return false
                val opponent = gameSession.participants.firstOrNull { it != nextTurn.playerId }
                opponent != null && (gameSession.playerHands[opponent]?.get(nextTurn.takenCard) ?: 0) > 0
            }
            is Turn.PlayDouble -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (!nextTurn.card.isCatCard()) return false
                if ((hand[nextTurn.card] ?: 0) < 2) return false
                val opponent = gameSession.participants.firstOrNull { it != nextTurn.playerId }
                opponent != null && (gameSession.playerHands[opponent]?.get(nextTurn.stolenCard) ?: 0) > 0
            }
            is Turn.PlayTriple -> {
                if (nextTurn.playerId != gameSession.whoseTurn) return false
                if (!nextTurn.card.isCatCard()) return false
                (hand[nextTurn.card] ?: 0) >= 3
            }
            is Turn.Pass -> false
        }
    }

    private fun CardType.isCatCard() = this in setOf(CardType.SPECIAL_1, CardType.SPECIAL_2, CardType.SPECIAL_3)
}

private class TestStatsService : IStatisticsService {
    override val playerRepository: IPlayerRepository get() = TODO()
    override fun getLeaderboard(number: UInt): List<Player> = TODO()
}
