package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStateSnapshot
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Turn
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.sql.Connection
import java.util.UUID

class SqliteGameRepository(private val connection: Connection) : IGameRepository {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Turn::class.java, TurnAdapter())
        .enableComplexMapKeySerialization()
        .create()

    private val listOfTurns = object : TypeToken<List<Turn>>() {}.type
    private val mapCardTypeInt = object : TypeToken<Map<CardType, Int>>() {}.type
    private val mapUuidMapCardTypeInt = object : TypeToken<Map<UUID, Map<CardType, Int>>>() {}.type
    private val listOfCardType = object : TypeToken<List<CardType>>() {}.type
    private val snapshotType = object : TypeToken<GameStateSnapshot>() {}.type

    override fun addSession(gameSession: GameSession) {
        deleteParticipants(gameSession.id)
        insertGameSession(gameSession)
        insertParticipants(gameSession.id, gameSession.participants)
    }

    private fun deleteParticipants(sessionId: UUID) {
        connection.prepareStatement(DatabaseManager.SQL_DELETE_SESSION_PARTICIPANTS).use { stmt ->
            stmt.setString(1, sessionId.toString())
            stmt.executeUpdate()
        }
    }

    private fun insertGameSession(gameSession: GameSession) {
        connection.prepareStatement(DatabaseManager.SQL_INSERT_GAME_SESSION).use { stmt ->
            stmt.setString(1, gameSession.id.toString())
            stmt.setString(2, gameSession.status.name)
            stmt.setString(3, gameSession.whoseTurn?.toString())
            stmt.setInt(4, gameSession.attackTurnsRemaining)
            stmt.setBoolean(5, gameSession.mustDefuse)
            stmt.setString(6, gameSession.winnerId?.toString())
            stmt.setString(7, gson.toJson(
                gameSession.turns.toList(),
                listOfTurns
            ))
            stmt.setString(8, gson.toJson(
                gameSession.drawPile.toList(),
                listOfCardType
            ))
            stmt.setString(9, gson.toJson(
                gameSession.discardPile,
                mapCardTypeInt
            ))
            stmt.setString(10, gson.toJson(
                gameSession.playerHands.mapValues { it.value.toMap() },
                mapUuidMapCardTypeInt
            ))
            stmt.setString(11, gameSession.initialState?.let {
                gson.toJson(it,
                    snapshotType)
            })
            stmt.executeUpdate()
        }
    }

    private fun insertParticipants(sessionId: UUID, participants: Set<UUID>) {
        connection.prepareStatement(DatabaseManager.SQL_INSERT_SESSION_PARTICIPANT).use { stmt ->
            for (pid in participants) {
                stmt.setString(1, sessionId.toString())
                stmt.setString(2, pid.toString())
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun getSession(sessionId: UUID): GameSession? {
        connection.prepareStatement(DatabaseManager.SQL_GET_GAME_SESSION).use { stmt ->
            stmt.setString(1, sessionId.toString())
            val rs = stmt.executeQuery()
            if (!rs.next()) return null
            return mapToGameSession(sessionId, rs)
        }
    }

    private fun fetchParticipants(sessionId: UUID): Set<UUID> {
        val participants = mutableSetOf<UUID>()
        connection.prepareStatement(DatabaseManager.SQL_GET_SESSION_PARTICIPANTS).use { stmt ->
            stmt.setString(1, sessionId.toString())
            val rs = stmt.executeQuery()
            while (rs.next()) {
                participants.add(UUID.fromString(rs.getString("player_id")))
            }
        }
        return participants
    }

    private fun mapToGameSession(sessionId: UUID, rs: java.sql.ResultSet): GameSession {
        val handsJson = rs.getString("player_hands")
        val handsRaw: Map<UUID, Map<CardType, Int>> = gson.fromJson(handsJson, mapUuidMapCardTypeInt)
        return GameSession(
            id = sessionId,
            participants = fetchParticipants(sessionId),
            turns = gson.fromJson<List<Turn>>(
                rs.getString("turns"),
                listOfTurns
            ).toMutableList(),
            discardPile = gson.fromJson<Map<CardType, Int>>(
                rs.getString("discard_pile"),
                mapCardTypeInt
            ).toMutableMap(),
            drawPile = gson.fromJson<List<CardType>>(
                rs.getString("draw_pile"),
                listOfCardType
            ).toMutableList(),
            status = GameStatus.valueOf(rs.getString("status")),
            whoseTurn = rs.getString("whose_turn")?.let { UUID.fromString(it) },
            playerHands = handsRaw.mapValues { it.value.toMutableMap() }.toMutableMap(),
            initialState = rs.getString(
                "initial_state"
            )?.let { gson.fromJson(it, snapshotType) },
            attackTurnsRemaining = rs.getInt("attack_turns_remaining"),
            mustDefuse = rs.getBoolean("must_defuse"),
            winnerId = rs.getString("winner_id")?.let { UUID.fromString(it) }
        )
    }

    override fun removeSession(sessionId: UUID) {
        deleteParticipants(sessionId)
        connection.prepareStatement(DatabaseManager.SQL_DELETE_GAME_SESSION).use { stmt ->
            stmt.setString(1, sessionId.toString())
            stmt.executeUpdate()
        }
    }

    override fun getAllSessions(): List<GameSession> {
        val ids = mutableListOf<UUID>()
        connection.prepareStatement(DatabaseManager.SQL_GET_ALL_GAME_SESSION_IDS).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                ids.add(UUID.fromString(rs.getString("id")))
            }
        }
        return ids.mapNotNull { getSession(it) }
    }
}
