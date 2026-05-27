package system

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

class FullGameSystemTest {
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
    fun `complete game with attack chain and defuse to explosion`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "Player1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "Player2"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(
            CardType.DEFUSE to 1,
            CardType.ATTACK to 1,
            CardType.SEE_THE_FUTURE to 1,
            CardType.SKIP to 1,
            CardType.NOPE to 1,
            CardType.SPECIAL_1 to 1,
            CardType.SPECIAL_2 to 1,
            CardType.SPECIAL_3 to 1
        )
        session.playerHands[p2] = mutableMapOf(
            CardType.DEFUSE to 2,
            CardType.SKIP to 1,
            CardType.NOPE to 1,
            CardType.FAVOR to 1,
            CardType.SPECIAL_1 to 1,
            CardType.SPECIAL_2 to 1,
            CardType.SPECIAL_3 to 1
        )
        session.drawPile.clear()
        session.drawPile.addAll(listOf(
            CardType.SPECIAL_1,
            CardType.EXPLODING_KITTEN,
            CardType.SPECIAL_2,
            CardType.SPECIAL_3
        ))

        assertTrue(engine.addTurn(sessionId, Turn.Attack(p1)))
        assertEquals(p2, gameRepo.getSession(sessionId)?.whoseTurn)
        assertEquals(2, gameRepo.getSession(sessionId)?.attackTurnsRemaining)

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.SPECIAL_1)))
        assertEquals(1, gameRepo.getSession(sessionId)?.attackTurnsRemaining)
        assertEquals(p2, gameRepo.getSession(sessionId)?.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.EXPLODING_KITTEN)))
        assertTrue(gameRepo.getSession(sessionId)?.mustDefuse == true)
        assertEquals(p2, gameRepo.getSession(sessionId)?.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.Defuse(p2, 0)))
        assertFalse(gameRepo.getSession(sessionId)?.mustDefuse == true)
        assertEquals(p1, gameRepo.getSession(sessionId)?.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.SeeTheFuture(p1)))
        assertEquals(p1, gameRepo.getSession(sessionId)?.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.EXPLODING_KITTEN)))
        assertTrue(gameRepo.getSession(sessionId)?.mustDefuse == true)

        assertTrue(engine.addTurn(sessionId, Turn.Defuse(p1, 1)))
        assertFalse(gameRepo.getSession(sessionId)?.mustDefuse == true)
        assertEquals(p2, gameRepo.getSession(sessionId)?.whoseTurn)

        val s = gameRepo.getSession(sessionId)!!
        s.drawPile.clear()
        s.drawPile.add(CardType.EXPLODING_KITTEN)
        s.playerHands[p2]!!.remove(CardType.DEFUSE)

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.EXPLODING_KITTEN)))
        assertTrue(gameRepo.getSession(sessionId)?.mustDefuse == true)
        assertFalse(engine.addTurn(sessionId, Turn.Defuse(p2, 0)))

        engine.endSession(sessionId, p1)

        val finalSession = gameRepo.getSession(sessionId)
        assertEquals(GameStatus.FINISHED, finalSession?.status)
        assertEquals(p1, finalSession?.winnerId)

        val loadedP1 = playerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.wins)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(1, loadedP1?.stats?.winRate)

        val loadedP2 = playerRepo.getPlayer(p2)
        assertEquals(1, loadedP2?.stats?.losses)
        assertEquals(1, loadedP2?.stats?.totalGames)
    }

    @Test
    fun `defuse survival then continue to end`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "Alice"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "Bob"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(
            CardType.DEFUSE to 2,
            CardType.SKIP to 1,
            CardType.SPECIAL_1 to 2,
            CardType.SPECIAL_2 to 1,
            CardType.SPECIAL_3 to 1
        )
        session.playerHands[p2] = mutableMapOf(
            CardType.DEFUSE to 1,
            CardType.NOPE to 1,
            CardType.SPECIAL_1 to 1,
            CardType.SPECIAL_2 to 2,
            CardType.SPECIAL_3 to 2
        )
        session.drawPile.clear()
        session.drawPile.addAll(listOf(CardType.EXPLODING_KITTEN, CardType.SPECIAL_1))

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.EXPLODING_KITTEN)))
        assertTrue(session.mustDefuse)

        assertTrue(engine.addTurn(sessionId, Turn.Defuse(p1, 1)))
        assertFalse(session.mustDefuse)
        assertEquals(p2, session.whoseTurn)
        assertEquals(2, session.drawPile.size)
        assertEquals(CardType.SPECIAL_1, session.drawPile[0])

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.SPECIAL_1)))
        assertEquals(p1, session.whoseTurn)

        engine.endSession(sessionId, p1)
        assertEquals(GameStatus.FINISHED, session.status)
        assertEquals(p1, session.winnerId)
    }

    @Test
    fun `game with no valid moves ends via force end`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "Alice"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "Bob"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(CardType.DEFUSE to 1)
        session.playerHands[p2] = mutableMapOf(CardType.DEFUSE to 1)
        session.drawPile.clear()

        assertFalse(engine.addTurn(sessionId, Turn.DrawCard(p1, CardType.DEFUSE)))
        assertFalse(engine.addTurn(sessionId, Turn.Attack(p1)))

        engine.endSession(sessionId, null)
        assertEquals(GameStatus.FINISHED, session.status)
        assertNull(session.winnerId)

        val loadedP1 = playerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(0, loadedP1?.stats?.wins)

        val loadedP2 = playerRepo.getPlayer(p2)
        assertEquals(1, loadedP2?.stats?.totalGames)
        assertEquals(0, loadedP2?.stats?.wins)
    }

    @Test
    fun `attack chain with skip and draw`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "Alice"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "Bob"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(
            CardType.DEFUSE to 1,
            CardType.ATTACK to 1,
            CardType.SPECIAL_1 to 4
        )
        session.playerHands[p2] = mutableMapOf(
            CardType.DEFUSE to 1,
            CardType.ATTACK to 1,
            CardType.SKIP to 1,
            CardType.SPECIAL_1 to 1
        )
        session.drawPile.clear()
        session.drawPile.addAll(listOf(
            CardType.SPECIAL_1,
            CardType.SPECIAL_1,
            CardType.SPECIAL_1,
            CardType.SPECIAL_1
        ))

        assertTrue(engine.addTurn(sessionId, Turn.Attack(p1)))
        assertEquals(2, session.attackTurnsRemaining)
        assertEquals(p2, session.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.Skip(p2)))
        assertEquals(1, session.attackTurnsRemaining)
        assertEquals(p2, session.whoseTurn)

        assertTrue(engine.addTurn(sessionId, Turn.DrawCard(p2, CardType.SPECIAL_1)))
        assertEquals(0, session.attackTurnsRemaining)
        assertEquals(p1, session.whoseTurn)
    }
}
