package com.github.pavelkuliaka.gui

import javafx.scene.Parent
import javafx.scene.control.Label
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.framework.junit5.ApplicationExtension

@ExtendWith(ApplicationExtension::class)
class NavigationTest : GuiTestBase() {

    @Test
    fun `navigateTo sets root in container`() {
        val view = object : ViewBase() {
            override val root: Parent = Label("HELLO")
        }
        Navigation.navigateTo(view)
        assertEquals(1, Navigation.container.children.size)
        val label = Navigation.container.children[0] as? Label
        assertEquals("HELLO", label?.text)
    }

    @Test
    fun `navigateTo calls onShown`() {
        var called = false
        val view = object : ViewBase() {
            override val root: Parent = Label("test")
            override fun onShown() { called = true }
        }
        Navigation.navigateTo(view)
        assertTrue(called)
    }
}
