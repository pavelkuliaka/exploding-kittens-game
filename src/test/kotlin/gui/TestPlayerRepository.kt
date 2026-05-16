package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.repository.JsonPlayerRepository

open class TestPlayerRepository : JsonPlayerRepository("") {
    override fun loadPlayers(): Boolean = true
    override fun savePlayers(): Boolean = true
}
