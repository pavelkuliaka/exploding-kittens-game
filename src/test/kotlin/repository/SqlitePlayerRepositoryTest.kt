package repository

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import com.github.pavelkuliaka.repository.SqlitePlayerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class SqlitePlayerRepositoryTest {
    private lateinit var repo: SqlitePlayerRepository

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
        repo = SqlitePlayerRepository(conn)
    }

    @Test
    fun `addPlayer and getPlayer work correctly`() {
        val player = createPlayer("Alice")
        repo.addPlayer(player)
        val retrieved = repo.getPlayer(player.id)
        assertNotNull(retrieved)
        assertEquals(player.id, retrieved?.id)
        assertEquals("Alice", retrieved?.name)
    }

    @Test
    fun `getPlayer returns null for non-existent id`() {
        val result = repo.getPlayer(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `removePlayer removes player`() {
        val player = createPlayer("Bob")
        repo.addPlayer(player)
        repo.removePlayer(player.id)
        assertNull(repo.getPlayer(player.id))
    }

    @Test
    fun `getAllPlayers returns all players`() {
        repo.addPlayer(createPlayer("Alice"))
        repo.addPlayer(createPlayer("Bob"))
        val all = repo.getAllPlayers()
        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Alice" })
        assertTrue(all.any { it.name == "Bob" })
    }

    @Test
    fun `player stats are persisted`() {
        val player = Player(
            id = UUID.randomUUID(), name = "Charlie", isPlaying = true,
            stats = PlayerStats(totalGames = 5, wins = 3, losses = 2, winRate = 60, defused = 1)
        )
        repo.addPlayer(player)
        val loaded = repo.getPlayer(player.id)
        assertEquals(5, loaded?.stats?.totalGames)
        assertEquals(3, loaded?.stats?.wins)
        assertEquals(2, loaded?.stats?.losses)
        assertEquals(60, loaded?.stats?.winRate)
        assertEquals(1, loaded?.stats?.defused)
        assertTrue(loaded?.isPlaying == true)
    }

    private fun createPlayer(name: String): Player {
        return Player(
            id = UUID.randomUUID(), name = name, isPlaying = false,
            stats = PlayerStats(totalGames = 0, wins = 0, losses = 0, winRate = 0, defused = 0)
        )
    }
}
