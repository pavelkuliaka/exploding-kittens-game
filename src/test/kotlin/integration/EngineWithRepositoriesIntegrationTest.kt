package com.github.pavelkuliaka.integration

import TestFixtures
import com.github.pavelkuliaka.engine.GameAdminEngine
import com.github.pavelkuliaka.model.*
import com.github.pavelkuliaka.repository.IGameRepository
import com.github.pavelkuliaka.repository.IPlayerRepository
import com.github.pavelkuliaka.repository.SqliteGameRepository
import com.github.pavelkuliaka.repository.SqlitePlayerRepository
import com.github.pavelkuliaka.service.StatisticsService
import com.github.pavelkuliaka.validation.RuleValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class EngineWithRepositoriesIntegrationTest {
    private lateinit var engine: GameAdminEngine
    private lateinit var gameRepo: IGameRepository
    private lateinit var playerRepo: IPlayerRepository
    private lateinit var statsService: StatisticsService

    @BeforeEach
    fun setUp() {
        val conn = DriverManager.getConnection("jdbc:sqlite:")
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS players (
                id TEXT PRIMARY KEY, name TEXT NOT NULL,
                is_playing INTEGER NOT NULL DEFAULT 0,
                total_games INTEGER NOT NULL DEFAULT 0,
                wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,
                win_rate INTEGER NOT NULL DEFAULT 0, defused INTEGER NOT NULL DEFAULT 0
            )
        """)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_sessions (
                id TEXT PRIMARY KEY, status TEXT NOT NULL DEFAULT 'ACTIVE',
                whose_turn TEXT, attack_turns_remaining INTEGER NOT NULL DEFAULT 0,
                must_defuse INTEGER NOT NULL DEFAULT 0, winner_id TEXT,
                turns TEXT NOT NULL DEFAULT '[]', draw_pile TEXT NOT NULL DEFAULT '[]',
                discard_pile TEXT NOT NULL DEFAULT '{}',
                player_hands TEXT NOT NULL DEFAULT '{}', initial_state TEXT
            )
        """)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS session_participants (
                session_id TEXT NOT NULL, player_id TEXT NOT NULL,
                PRIMARY KEY (session_id, player_id)
            )
        """)
        gameRepo = SqliteGameRepository(conn)
        playerRepo = SqlitePlayerRepository(conn)
        val validator = RuleValidator()
        statsService = StatisticsService(playerRepo)
        engine = GameAdminEngine(
            gameRepo, playerRepo, validator, statsService
        )
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
        gameRepo.addSession(session)

        engine.endSession(sessionId, p1)

        val loadedP1 = playerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.wins)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(1, loadedP1?.stats?.winRate)

        val loadedP2 = playerRepo.getPlayer(p2)
        assertEquals(1, loadedP2?.stats?.losses)
        assertEquals(0, loadedP2?.stats?.wins)
    }

    @Test
    fun `player isPlaying flag preserved`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        playerRepo.addPlayer(TestFixtures.createPlayer(p1, "P1"))
        playerRepo.addPlayer(TestFixtures.createPlayer(p2, "P2"))

        engine.startNewSession(p1, p2)

        assertTrue(playerRepo.getPlayer(p1)?.isPlaying == true)
        assertTrue(playerRepo.getPlayer(p2)?.isPlaying == true)
    }

    @Test
    fun `simple session roundtrip`() {
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
        gameRepo.addSession(session)

        val loaded = gameRepo.getSession(sessionId)
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
        playerRepo.addPlayer(Player(
            p1,
            "P1",
            false,
            PlayerStats(5, 3, 2, 60, 0)
        ))
        playerRepo.addPlayer(Player(
            p2,
            "P2",
            false,
            PlayerStats(3, 1, 2, 33, 0)
        ))

        val statsService2 = StatisticsService(playerRepo)
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

        val loadedP1 = playerRepo.getPlayer(p1)
        assertEquals(1, loadedP1?.stats?.totalGames)
        assertEquals(0, loadedP1?.stats?.wins)
        assertEquals(0, loadedP1?.stats?.losses)
    }
}
