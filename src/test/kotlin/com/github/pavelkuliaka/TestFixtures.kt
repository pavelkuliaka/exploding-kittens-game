package com.github.pavelkuliaka

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import java.util.UUID

object TestFixtures {
    fun createPlayer(id: UUID = UUID.randomUUID(), name: String = "TestPlayer"): Player {
        return Player(
            id = id,
            name = name,
            isPlaying = false,
            stats = PlayerStats(totalGames = 0, wins = 0, losses = 0, winRate = 0, defused = 0)
        )
    }
}
