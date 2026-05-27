package com.github.pavelkuliaka.gui

import com.github.pavelkuliaka.model.*
import javafx.scene.Node
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxRobot
import org.testfx.framework.junit5.ApplicationExtension
import java.util.UUID

@ExtendWith(ApplicationExtension::class)
class GamePlayFlowTest : GuiTestBase() {
    private val p1Id = UUID.randomUUID()
    private val p2Id = UUID.randomUUID()
    private val sid = UUID.randomUUID()

    @BeforeEach
    fun populateData() {
        AppDependencies.playerRepository.addPlayer(Player(
            p1Id,
            "Alice",
            false,
            PlayerStats(0, 0, 0, 0, 0))
        )
        AppDependencies.playerRepository.addPlayer(Player(
            p2Id,
            "Bob",
            false,
            PlayerStats(0, 0, 0, 0, 0))
        )
    }

    private fun createActiveSession(
        p1Hand: MutableMap<CardType, Int> = mutableMapOf(
            CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.SKIP to 1
        ),
        p2Hand: MutableMap<CardType, Int> = mutableMapOf(CardType.DEFUSE to 1),
        drawPile: MutableList<CardType> = mutableListOf(CardType.EXPLODING_KITTEN, CardType.SKIP),
        mustDefuse: Boolean = false,
        status: GameStatus = GameStatus.ACTIVE,
        winnerId: UUID? = null,
    ): GameSession {
        val session = GameSession(
            id = sid, participants = setOf(p1Id, p2Id),
            turns = mutableListOf(), discardPile = mutableMapOf(),
            drawPile = drawPile,
            status = status, whoseTurn = p1Id,
            playerHands = mutableMapOf(p1Id to p1Hand, p2Id to p2Hand),
            mustDefuse = mustDefuse,
            winnerId = winnerId
        )
        AppDependencies.gameRepository.addSession(session)
        AppDependencies.activeSessionId = sid
        return session
    }

    @Test
    fun `game view shows player names and turn info`(robot: FxRobot) {
        createActiveSession()
        robot.interact { GamePlayView().also { Navigation.navigateTo(it) } }

        robot.lookup("Alice").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show Alice's label")
        }
        robot.lookup("Bob").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show Bob's label")
        }
        robot.lookup("Turn: Alice (attack turns remaining: 0)").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show turn info")
        }
    }

    @Test
    fun `game view shows draw pile size`(robot: FxRobot) {
        createActiveSession()
        robot.interact { GamePlayView().also { Navigation.navigateTo(it) } }

        robot.lookup("Draw pile: 2 cards (top: EXPLODING_KITTEN)").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show draw pile info")
        }
    }

    @Test
    fun `must defuse shows warning banner`(robot: FxRobot) {
        createActiveSession(p1Hand = mutableMapOf(CardType.DEFUSE to 1), mustDefuse = true)
        robot.interact { GamePlayView().also { Navigation.navigateTo(it) } }

        robot.lookup("Alice MUST DEFUSE").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show MUST DEFUSE banner")
        }
    }

    @Test
    fun `finished game shows message`(robot: FxRobot) {
        createActiveSession(status = GameStatus.FINISHED, winnerId = p2Id)
        robot.interact { GamePlayView().also { Navigation.navigateTo(it) } }

        robot.lookup("Game session is finished").tryQuery<Node>().also {
            Assertions.assertNotNull(it, "Should show finished message")
        }
    }
}
