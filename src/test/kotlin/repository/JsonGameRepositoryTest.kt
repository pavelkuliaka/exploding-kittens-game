package repository

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.repository.JsonGameRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID

class JsonGameRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `addSession and getSession work correctly`() {
        val file = File(tempDir.toFile(), "games.json")
        val repo = JsonGameRepository(file.absolutePath)
        val session = createSession()

        repo.addSession(session)
        val retrieved = repo.getSession(session.id)

        assertNotNull(retrieved)
        assertEquals(session.id, retrieved?.id)
        assertEquals(session.participants, retrieved?.participants)
    }

    @Test
    fun `getSession returns null for non-existent id`() {
        val file = File(tempDir.toFile(), "games.json")
        val repo = JsonGameRepository(file.absolutePath)

        val result = repo.getSession(UUID.randomUUID())

        assertNull(result)
    }

    @Test
    fun `removeSession removes session`() {
        val file = File(tempDir.toFile(), "games.json")
        val repo = JsonGameRepository(file.absolutePath)
        val session = createSession()

        repo.addSession(session)
        repo.removeSession(session.id)
        val retrieved = repo.getSession(session.id)

        assertNull(retrieved)
    }

    @Test
    fun `saveSessions and loadSessions roundtrip`() {
        val file = File(tempDir.toFile(), "games.json")
        val repo = JsonGameRepository(file.absolutePath)
        val session = createSession()

        repo.addSession(session)
        val saved = repo.saveSessions()
        assertTrue(saved)

        val repo2 = JsonGameRepository(file.absolutePath)
        val loaded = repo2.loadSessions()
        assertTrue(loaded)

        val retrieved = repo2.getSession(session.id)
        assertNotNull(retrieved)
        assertEquals(session.id, retrieved?.id)
        assertEquals(session.participants, retrieved?.participants)
        assertEquals(session.status, retrieved?.status)
        assertEquals(session.whoseTurn, retrieved?.whoseTurn)
    }

    @Test
    fun `loadSessions returns false when file does not exist`() {
        val file = File(tempDir.toFile(), "nonexistent.json")
        val repo = JsonGameRepository(file.absolutePath)

        val result = repo.loadSessions()

        assertFalse(result)
    }

    @Test
    fun `sessions map is accessible`() {
        val file = File(tempDir.toFile(), "games.json")
        val repo = JsonGameRepository(file.absolutePath)
        val session = createSession()

        repo.addSession(session)

        assertEquals(1, repo.sessions.size)
        assertTrue(repo.sessions.containsKey(session.id))
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
