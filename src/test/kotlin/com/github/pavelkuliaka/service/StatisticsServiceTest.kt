package com.github.pavelkuliaka.service

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import com.github.pavelkuliaka.repository.IPlayerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StatisticsServiceTest {

    @Test
    fun `getLeaderboard sorts by wins ascending`() {
        val p1 = createPlayer("Alice", wins = 5)
        val p2 = createPlayer("Bob", wins = 10)
        val p3 = createPlayer("Charlie", wins = 3)
        val repo = FakePlayerRepo(listOf(p1, p2, p3))
        val service = StatisticsService(repo)

        val result = service.getLeaderboard(3u)

        assertEquals("Charlie", result[0].name)
        assertEquals("Alice", result[1].name)
        assertEquals("Bob", result[2].name)
    }

    @Test
    fun `getLeaderboard limits results by number`() {
        val players = listOf(
            createPlayer("A", wins = 1),
            createPlayer("B", wins = 2),
            createPlayer("C", wins = 3),
            createPlayer("D", wins = 4)
        )
        val repo = FakePlayerRepo(players)
        val service = StatisticsService(repo)

        val result = service.getLeaderboard(2u)

        assertEquals(2, result.size)
        assertEquals("A", result[0].name)
        assertEquals("B", result[1].name)
    }

    @Test
    fun `getLeaderboard returns empty list when no players`() {
        val repo = FakePlayerRepo(emptyList())
        val service = StatisticsService(repo)

        val result = service.getLeaderboard(5u)

        assertTrue(result.isEmpty())
    }

    private fun createPlayer(name: String, wins: Int): Player {
        return Player(
            id = UUID.randomUUID(),
            name = name,
            isPlaying = false,
            stats = PlayerStats(
                totalGames = wins,
                wins = wins,
                losses = 0,
                winRate = 100,
                defused = 0
            )
        )
    }

    private class FakePlayerRepo(private val players: List<Player>) : IPlayerRepository {
        override fun addPlayer(player: Player) {}
        override fun getPlayer(playerId: UUID): Player? = players.find { it.id == playerId }
        override fun removePlayer(playerId: UUID) {}
        override fun getAllPlayers(): List<Player> = players
    }
}
