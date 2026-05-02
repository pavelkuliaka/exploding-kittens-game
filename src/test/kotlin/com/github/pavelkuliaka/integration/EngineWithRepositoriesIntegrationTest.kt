package com.github.pavelkuliaka.integration

import com.github.pavelkuliaka.TestFixtures
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

class EngineWithRepositoriesIntegrationTest {
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
    fun `player stats survive save and load across engine restart`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "P2"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(CardType.DEFUSE to 1)
        session.playerHands[p2] = mutableMapOf(CardType.DEFUSE to 1)
        session.drawPile.clear()

        engine.endSession(sessionId, p1)

        assertTrue(playerRepo.savePlayers())

        val newPlayerRepo = JsonPlayerRepository(playersPath)
        assertTrue(newPlayerRepo.loadPlayers())

        val loadedP1 = newPlayerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.wins)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(1, loadedP1?.stats?.winRate)

        val loadedP2 = newPlayerRepo.getPlayer(p2)
        assertEquals(1, loadedP2?.stats?.losses)
        assertEquals(0, loadedP2?.stats?.wins)
    }

    @Test
    fun `player isPlaying flag preserved after save and load`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "P2"))

        engine.startNewSession(p1, p2)

        assertTrue(playerRepo.savePlayers())

        val newPlayerRepo = JsonPlayerRepository(playersPath)
        assertTrue(newPlayerRepo.loadPlayers())

        assertTrue(newPlayerRepo.getPlayer(p1)?.isPlaying == true)
        assertTrue(newPlayerRepo.getPlayer(p2)?.isPlaying == true)
    }

    @Test
    fun `simple session survives save and load`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "P2"))

        val sessionId = engine.startNewSession(p1, p2)
        val session = gameRepo.getSession(sessionId)!!
        session.playerHands[p1] = mutableMapOf(CardType.DEFUSE to 1)
        session.playerHands[p2] = mutableMapOf(CardType.DEFUSE to 1)
        session.drawPile.clear()
        session.drawPile.addAll(listOf(CardType.SPECIAL_1, CardType.SPECIAL_2))

        assertTrue(gameRepo.saveSessions())

        val newGameRepo = JsonGameRepository(sessionsPath)
        assertTrue(newGameRepo.loadSessions())

        val loaded = newGameRepo.getSession(sessionId)
        assertNotNull(loaded)
        assertEquals(setOf(p1, p2), loaded?.participants)
        assertEquals(GameStatus.ACTIVE, loaded?.status)
        assertEquals(p1, loaded?.whoseTurn)
        assertEquals(2, loaded?.drawPile?.size)
    }

    @Test
    fun `multiple players stats accumulate correctly`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(Player(p1, "P1", false, PlayerStats(5, 3, 2, 60, 0)))
        playerRepo.addPlayer(Player(p2, "P2", false, PlayerStats(3, 1, 2, 33, 0)))

        assertTrue(playerRepo.savePlayers())

        val newPlayerRepo = JsonPlayerRepository(playersPath)
        assertTrue(newPlayerRepo.loadPlayers())

        val statsService2 = StatisticsService(newPlayerRepo)
        val leaderboard = statsService2.getLeaderboard(2u)
        assertEquals(2, leaderboard.size)
        assertEquals("P2", leaderboard[0].name)
        assertEquals("P1", leaderboard[1].name)
    }

    @Test
    fun `endSession without winner persists correctly`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "P2"))

        val sessionId = engine.startNewSession(p1, p2)
        engine.endSession(sessionId, null)

        assertTrue(playerRepo.savePlayers())

        val newPlayerRepo = JsonPlayerRepository(playersPath)
        assertTrue(newPlayerRepo.loadPlayers())

        val loadedP1 = newPlayerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(0, loadedP1?.stats?.wins)
        assertEquals(0, loadedP1?.stats?.losses)
    }
}
