package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.Player
import com.github.pavelkuliaka.model.PlayerStats
import java.sql.Connection
import java.util.UUID

class SqlitePlayerRepository(private val connection: Connection) : IPlayerRepository {
    override fun addPlayer(player: Player) {
        connection.prepareStatement(DatabaseManager.SQL_INSERT_PLAYER).use { stmt ->
            stmt.setString(1, player.id.toString())
            stmt.setString(2, player.name)
            stmt.setBoolean(3, player.isPlaying)
            stmt.setInt(4, player.stats.totalGames)
            stmt.setInt(5, player.stats.wins)
            stmt.setInt(6, player.stats.losses)
            stmt.setInt(7, player.stats.winRate)
            stmt.setInt(8, player.stats.defused)
            stmt.executeUpdate()
        }
    }

    override fun getPlayer(playerId: UUID): Player? {
        connection.prepareStatement(DatabaseManager.SQL_GET_PLAYER).use { stmt ->
            stmt.setString(1, playerId.toString())
            val rs = stmt.executeQuery()
            if (!rs.next()) return null
            return mapToPlayer(rs)
        }
    }

    override fun removePlayer(playerId: UUID) {
        connection.prepareStatement(DatabaseManager.SQL_DELETE_PLAYER).use { stmt ->
            stmt.setString(1, playerId.toString())
            stmt.executeUpdate()
        }
    }

    override fun getAllPlayers(): List<Player> {
        val result = mutableListOf<Player>()
        connection.prepareStatement(DatabaseManager.SQL_GET_ALL_PLAYERS).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result.add(mapToPlayer(rs))
            }
        }
        return result
    }

    private fun mapToPlayer(rs: java.sql.ResultSet): Player {
        return Player(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            isPlaying = rs.getBoolean("is_playing"),
            stats = PlayerStats(
                totalGames = rs.getInt("total_games"),
                wins = rs.getInt("wins"),
                losses = rs.getInt("losses"),
                winRate = rs.getInt("win_rate"),
                defused = rs.getInt("defused")
            )
        )
    }
}
