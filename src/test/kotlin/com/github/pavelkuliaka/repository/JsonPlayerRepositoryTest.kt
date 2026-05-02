package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID

class JsonPlayerRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `addPlayer and getPlayer work correctly`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)
        val player = createPlayer("Alice")

        repo.addPlayer(player)
        val retrieved = repo.getPlayer(player.id)

        assertNotNull(retrieved)
        assertEquals(player.id, retrieved?.id)
        assertEquals("Alice", retrieved?.name)
    }

    @Test
    fun `getPlayer returns null for non-existent id`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)

        val result = repo.getPlayer(UUID.randomUUID())

        assertNull(result)
    }

    @Test
    fun `removePlayer removes player`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)
        val player = createPlayer("Bob")

        repo.addPlayer(player)
        repo.removePlayer(player.id)
        val retrieved = repo.getPlayer(player.id)

        assertNull(retrieved)
    }

    @Test
    fun `getAllPlayers returns all players`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)
        val p1 = createPlayer("Alice")
        val p2 = createPlayer("Bob")

        repo.addPlayer(p1)
        repo.addPlayer(p2)
        val all = repo.getAllPlayers()

        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Alice" })
        assertTrue(all.any { it.name == "Bob" })
    }

    @Test
    fun `savePlayers and loadPlayers roundtrip`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)
        val player = createPlayer("Charlie")

        repo.addPlayer(player)
        val saved = repo.savePlayers()
        assertTrue(saved)

        val repo2 = JsonPlayerRepository(file.absolutePath)
        val loaded = repo2.loadPlayers()
        assertTrue(loaded)

        val retrieved = repo2.getPlayer(player.id)
        assertNotNull(retrieved)
        assertEquals(player.id, retrieved?.id)
        assertEquals("Charlie", retrieved?.name)
    }

    @Test
    fun `players map is accessible`() {
        val file = File(tempDir.toFile(), "players.json")
        val repo = JsonPlayerRepository(file.absolutePath)
        val player = createPlayer("Dave")

        repo.addPlayer(player)

        assertEquals(1, repo.players.size)
        assertTrue(repo.players.containsKey(player.id))
    }

    private fun createPlayer(name: String): Player {
        return Player(
            id = UUID.randomUUID(),
            name = name,
            isPlaying = false,
            stats = PlayerStats(totalGames = 0, wins = 0, losses = 0, winRate = 0, defused = 0)
        )
    }
}
