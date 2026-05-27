package com.github.pavelkuliaka.repository

import java.sql.Connection
import java.sql.DriverManager

object DatabaseManager {
    private const val DB_URL = "jdbc:sqlite:game.db"

    const val SQL_INSERT_GAME_SESSION = """
        INSERT OR REPLACE INTO game_sessions
        (id, status, whose_turn, attack_turns_remaining, must_defuse, winner_id,
         turns, draw_pile, discard_pile, player_hands, initial_state)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    const val SQL_INSERT_SESSION_PARTICIPANT =
        "INSERT OR IGNORE INTO session_participants (session_id, player_id) VALUES (?, ?)"
    const val SQL_DELETE_SESSION_PARTICIPANTS =
        "DELETE FROM session_participants WHERE session_id = ?"
    const val SQL_DELETE_GAME_SESSION = "DELETE FROM game_sessions WHERE id = ?"
    const val SQL_GET_GAME_SESSION = "SELECT * FROM game_sessions WHERE id = ?"
    const val SQL_GET_SESSION_PARTICIPANTS =
        "SELECT player_id FROM session_participants WHERE session_id = ?"
    const val SQL_GET_ALL_GAME_SESSION_IDS = "SELECT id FROM game_sessions"

    const val SQL_INSERT_PLAYER = """
        INSERT OR REPLACE INTO players
        (id, name, is_playing, total_games, wins, losses, win_rate, defused)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """
    const val SQL_GET_PLAYER = "SELECT * FROM players WHERE id = ?"
    const val SQL_DELETE_PLAYER = "DELETE FROM players WHERE id = ?"
    const val SQL_GET_ALL_PLAYERS = "SELECT * FROM players"

    const val SQL_CREATE_TABLE_PLAYERS = """
        CREATE TABLE IF NOT EXISTS players (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            is_playing INTEGER NOT NULL DEFAULT 0,
            total_games INTEGER NOT NULL DEFAULT 0,
            wins INTEGER NOT NULL DEFAULT 0,
            losses INTEGER NOT NULL DEFAULT 0,
            win_rate INTEGER NOT NULL DEFAULT 0,
            defused INTEGER NOT NULL DEFAULT 0
        )
    """
    const val SQL_CREATE_TABLE_GAME_SESSIONS = """
        CREATE TABLE IF NOT EXISTS game_sessions (
            id TEXT PRIMARY KEY,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            whose_turn TEXT,
            attack_turns_remaining INTEGER NOT NULL DEFAULT 0,
            must_defuse INTEGER NOT NULL DEFAULT 0,
            winner_id TEXT,
            turns TEXT NOT NULL DEFAULT '[]',
            draw_pile TEXT NOT NULL DEFAULT '[]',
            discard_pile TEXT NOT NULL DEFAULT '{}',
            player_hands TEXT NOT NULL DEFAULT '{}',
            initial_state TEXT
        )
    """
    const val SQL_CREATE_TABLE_SESSION_PARTICIPANTS = """
        CREATE TABLE IF NOT EXISTS session_participants (
            session_id TEXT NOT NULL,
            player_id TEXT NOT NULL,
            PRIMARY KEY (session_id, player_id)
        )
    """

    private var _connection: Connection? = null
    val connection: Connection
        get() {
            if (_connection == null || _connection!!.isClosed) {
                init()
            }
            return _connection!!
        }

    fun init() {
        Class.forName("org.sqlite.JDBC")
        _connection = DriverManager.getConnection(DB_URL)
        createTables()
    }

    private fun createTables() {
        val stmt = _connection!!.createStatement()
        stmt.execute(SQL_CREATE_TABLE_PLAYERS)
        stmt.execute(SQL_CREATE_TABLE_GAME_SESSIONS)
        stmt.execute(SQL_CREATE_TABLE_SESSION_PARTICIPANTS)
        stmt.close()
    }

    fun close() {
        _connection?.close()
        _connection = null
    }
}
