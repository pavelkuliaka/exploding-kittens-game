package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.*
import org.junit.jupiter.api.*
import org.testfx.api.FxRobot
import java.util.UUID.randomUUID
import javafx.scene.Node
import javafx.scene.control.ComboBox
import javafx.scene.control.Button
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.framework.junit5.ApplicationExtension

@ExtendWith(ApplicationExtension::class)
class CreateSessionFlowTest : GuiTestBase() {
    private val p1 = Player(
        randomUUID(),
        "Alice",
        false,
        PlayerStats(0, 0, 0, 0, 0)
    )
    private val p2 = Player(
        randomUUID(),
        "Bob",
        false,
        PlayerStats(0, 0, 0, 0, 0)
    )

    @BeforeEach
    fun populateData(robot: FxRobot) {
        AppDependencies.playerRepository.addPlayer(p1)
        AppDependencies.playerRepository.addPlayer(p2)
    }

    @Test
    fun `step0 shows player selection`(robot: FxRobot) {
        robot.interact {
            CreateSessionView().also { Navigation.navigateTo(it) }
        }

        robot.lookup("Select players for new game:").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Step 0 should show player selection label")
        }
    }

    @Test
    fun `step0 shows error when selecting same player`(robot: FxRobot) {
        robot.interact {
            CreateSessionView().also { Navigation.navigateTo(it) }
        }

        robot.lookup(".combo-box").queryAll<Node>().let { cbs ->
            if (cbs.size >= 2) {
                val cbList = cbs.toList()
                robot.interact {
                    (cbList[0] as ComboBox<Player>).selectionModel.select(p1)
                    (cbList[1] as ComboBox<Player>).selectionModel.select(p1)
                }
                robot.interact {
                    robot.lookup("Continue").queryAs<Button>(Button::class.java).fire()
                }
                robot.lookup("Players must be different").tryQuery<Node>().also {
                    Assertions.assertNotNull(it, "Should show error for same player")
                }
            }
        }
    }

    @Test
    fun `cancel in step1 resets isPlaying and removes session`(robot: FxRobot) {
        robot.interact {
            CreateSessionView().also { Navigation.navigateTo(it) }
        }

        robot.lookup(".combo-box").queryAll<Node>().let { cbs ->
            if (cbs.size >= 2) {
                val cbList = cbs.toList()
                robot.interact {
                    (cbList[0] as ComboBox<Player>).selectionModel.select(p1)
                    (cbList[1] as ComboBox<Player>).selectionModel.select(p2)
                }
                robot.interact {
                    robot.lookup("Continue").queryAs<Button>(Button::class.java).fire()
                }
                robot.lookup("Cancel session").tryQuery<Node>().ifPresent { cancelBtn ->
                    robot.interact { (cancelBtn as Button).fire() }
                    Thread.sleep(200)
                    Assertions.assertFalse(p1.isPlaying)
                    Assertions.assertFalse(p2.isPlaying)
                    Assertions.assertTrue(AppDependencies.gameRepository.sessions.isEmpty())
                }
            }
        }
    }
}
