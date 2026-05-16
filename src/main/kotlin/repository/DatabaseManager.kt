package com.github.pavelkuliaka.repository

import java.sql.Connection
import java.sql.DriverManager

object DatabaseManager {
    private const val DB_URL = "jdbc:sqlite:game.db"

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
        stmt.execute("""
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
        """)
        stmt.execute("""
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
        """)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS session_participants (
                session_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                PRIMARY KEY (session_id, player_id)
            )
        """)
        stmt.close()
    }

    fun close() {
        _connection?.close()
        _connection = null
    }
}
