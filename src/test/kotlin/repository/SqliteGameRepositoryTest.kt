package repository

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.repository.SqliteGameRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class SqliteGameRepositoryTest {
    private lateinit var repo: SqliteGameRepository

    @BeforeEach
    fun setUp() {
        val conn = DriverManager.getConnection("jdbc:sqlite:")
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
        repo = SqliteGameRepository(conn)
    }

    @Test
    fun `addSession and getSession work correctly`() {
        val session = createSession()
        repo.addSession(session)
        val retrieved = repo.getSession(session.id)
        assertNotNull(retrieved)
        assertEquals(session.id, retrieved?.id)
        assertEquals(session.participants, retrieved?.participants)
    }

    @Test
    fun `getSession returns null for non-existent id`() {
        val result = repo.getSession(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `removeSession removes session`() {
        val session = createSession()
        repo.addSession(session)
        repo.removeSession(session.id)
        assertNull(repo.getSession(session.id))
    }

    @Test
    fun `session fields preserved after roundtrip`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(CardType.SKIP to 1),
            drawPile = mutableListOf(CardType.ATTACK, CardType.NOPE),
            status = GameStatus.ACTIVE,
            whoseTurn = p1,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1)
            )
        )
        repo.addSession(session)
        val loaded = repo.getSession(session.id)
        assertNotNull(loaded)
        assertEquals(session.status, loaded?.status)
        assertEquals(session.whoseTurn, loaded?.whoseTurn)
        assertEquals(session.drawPile, loaded?.drawPile)
        assertEquals(session.discardPile, loaded?.discardPile)
        assertEquals(session.playerHands, loaded?.playerHands)
    }

    @Test
    fun `getSession after re-add returns updated session`() {
        val session = createSession()
        repo.addSession(session)
        val modified = createSession().copy(id = session.id)
        repo.addSession(modified)
        val loaded = repo.getSession(session.id)
        assertNotNull(loaded)
        assertEquals(modified.participants, loaded?.participants)
    }

    private fun createSession(): GameSession {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        return GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(CardType.SKIP to 1),
            drawPile = mutableListOf(CardType.ATTACK, CardType.NOPE),
            status = GameStatus.ACTIVE,
            whoseTurn = p1,
            playerHands = mutableMapOf(
                p1 to mutableMapOf(CardType.DEFUSE to 1),
                p2 to mutableMapOf(CardType.DEFUSE to 1)
            )
        )
    }
}
