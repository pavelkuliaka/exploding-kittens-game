package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.repository.JsonGameRepository

open class TestGameRepository : JsonGameRepository("") {
    override fun loadSessions(): Boolean = true
    override fun saveSessions(): Boolean = true
}
