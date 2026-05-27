package com.github.pavelkuliaka.gui

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.framework.junit5.ApplicationExtension

@ExtendWith(ApplicationExtension::class)
abstract class GuiTestBase {
    @BeforeEach
    fun setUpRepos() {
        AppDependencies.gameRepository = TestGameRepository()
        AppDependencies.playerRepository = TestPlayerRepository()
        AppDependencies.activeSessionId = null
        AppDependencies.notificationMessage = null
    }
}
