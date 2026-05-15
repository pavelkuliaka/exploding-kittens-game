package validation

import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.DeckComposition
import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.validation.RuleValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class RuleValidatorTest {
    private val validator = RuleValidator()

    private fun makeSession(
        p1Id: UUID,
        p2Id: UUID,
        status: GameStatus = GameStatus.ACTIVE,
        whoseTurn: UUID? = null,
        p1Hand: Map<CardType, Int> = emptyMap(),
        p2Hand: Map<CardType, Int> = emptyMap(),
        drawPile: MutableList<CardType> = mutableListOf(),
        mustDefuse: Boolean = false,
        turns: MutableList<Turn> = mutableListOf()
    ): GameSession {
        return GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1Id, p2Id),
            turns = turns,
            discardPile = mutableMapOf(),
            drawPile = drawPile,
            status = status,
            whoseTurn = whoseTurn,
            playerHands = mutableMapOf(
                p1Id to p1Hand.toMutableMap(),
                p2Id to p2Hand.toMutableMap()
            ),
            mustDefuse = mustDefuse
        )
    }

    @Test
    fun `validateTurn returns false for FINISHED session`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, status = GameStatus.FINISHED, whoseTurn = p1)
        assertFalse(validator.validateTurn(session, Turn.Attack(p1)))
    }

    @Test
    fun `validateTurn returns false if player not a participant`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1)
        val nonPlayer = UUID.randomUUID()
        assertFalse(validator.validateTurn(session, Turn.Attack(nonPlayer)))
    }

    @Test
    fun `validateTurn DrawCard returns true when valid`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf(CardType.ATTACK))
        assertTrue(validator.validateTurn(session, Turn.DrawCard(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn DrawCard returns false when mustDefuse`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf(CardType.ATTACK), mustDefuse = true)
        assertFalse(validator.validateTurn(session, Turn.DrawCard(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn DrawCard returns false when drawPile empty`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf())
        assertFalse(validator.validateTurn(session, Turn.DrawCard(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn DrawCard returns false when top card mismatch`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf(CardType.ATTACK))
        assertFalse(validator.validateTurn(session, Turn.DrawCard(p1, CardType.NOPE)))
    }

    @Test
    fun `validateTurn DrawCard returns false when not player's turn`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p2, p1Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf(CardType.ATTACK))
        assertFalse(validator.validateTurn(session, Turn.DrawCard(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn Defuse returns true when mustDefuse`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), mustDefuse = true)
        assertTrue(validator.validateTurn(session, Turn.Defuse(p1, 0)))
    }

    @Test
    fun `validateTurn Defuse returns false when not mustDefuse`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), mustDefuse = false)
        assertFalse(validator.validateTurn(session, Turn.Defuse(p1, 0)))
    }

    @Test
    fun `validateTurn Defuse returns false when no DEFUSE card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.ATTACK to 1), mustDefuse = true)
        assertFalse(validator.validateTurn(session, Turn.Defuse(p1, 0)))
    }

    @Test
    fun `validateTurn Attack returns true when has card and is turn`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1))
        assertTrue(validator.validateTurn(session, Turn.Attack(p1)))
    }

    @Test
    fun `validateTurn Attack returns false when no card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1))
        assertFalse(validator.validateTurn(session, Turn.Attack(p1)))
    }

    @Test
    fun `validateTurn Skip returns true when has card and is turn`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SKIP to 1))
        assertTrue(validator.validateTurn(session, Turn.Skip(p1)))
    }

    @Test
    fun `validateTurn Skip returns false when no card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1))
        assertFalse(validator.validateTurn(session, Turn.Skip(p1)))
    }

    @Test
    fun `validateTurn SeeTheFuture returns true when has card and is turn`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SEE_THE_FUTURE to 1))
        assertTrue(validator.validateTurn(session, Turn.SeeTheFuture(p1)))
    }

    @Test
    fun `validateTurn SeeTheFuture returns false when no card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1))
        assertFalse(validator.validateTurn(session, Turn.SeeTheFuture(p1)))
    }

    @Test
    fun `validateTurn Shuffle returns true when valid`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1), drawPile = mutableListOf(CardType.ATTACK, CardType.SKIP))
        assertTrue(validator.validateTurn(session, Turn.Shuffle(p1, listOf(CardType.SKIP, CardType.ATTACK))))
    }

    @Test
    fun `validateTurn Shuffle returns false when size mismatch`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1), drawPile = mutableListOf(CardType.ATTACK, CardType.SKIP))
        assertFalse(validator.validateTurn(session, Turn.Shuffle(p1, listOf(CardType.ATTACK))))
    }

    @Test
    fun `validateTurn Shuffle returns false when composition mismatch`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SHUFFLE to 1), drawPile = mutableListOf(CardType.ATTACK, CardType.ATTACK))
        assertFalse(validator.validateTurn(session, Turn.Shuffle(p1, listOf(CardType.ATTACK, CardType.SKIP))))
    }

    @Test
    fun `validateTurn Favor returns true when opponent has card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1), p2Hand = mapOf(CardType.ATTACK to 1))
        assertTrue(validator.validateTurn(session, Turn.Favor(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn Favor returns false when opponent does not have card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.FAVOR to 1), p2Hand = mapOf())
        assertFalse(validator.validateTurn(session, Turn.Favor(p1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn PlayDouble returns true when valid`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 2), p2Hand = mapOf(CardType.ATTACK to 1))
        assertTrue(validator.validateTurn(session, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn PlayDouble returns false when less than 2 cards`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 1), p2Hand = mapOf(CardType.ATTACK to 1))
        assertFalse(validator.validateTurn(session, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn PlayDouble returns false when opponent has no stolenCard`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SPECIAL_1 to 2), p2Hand = mapOf())
        assertFalse(validator.validateTurn(session, Turn.PlayDouble(p1, CardType.SPECIAL_1, CardType.ATTACK)))
    }

    @Test
    fun `validateTurn PlayTriple returns true when has 3 cards`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SPECIAL_2 to 3))
        assertTrue(validator.validateTurn(session, Turn.PlayTriple(p1, CardType.SPECIAL_2)))
    }

    @Test
    fun `validateTurn PlayTriple returns false when less than 3 cards`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.SPECIAL_2 to 2))
        assertFalse(validator.validateTurn(session, Turn.PlayTriple(p1, CardType.SPECIAL_2)))
    }

    @Test
    fun `validateTurn Nope returns false when target is Defuse`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.NOPE to 1), turns = mutableListOf(Turn.Defuse(p1, 0)))
        assertFalse(validator.validateTurn(session, Turn.Nope(p1, 0)))
    }

    @Test
    fun `validateTurn Nope returns false when target is DrawCard`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.NOPE to 1), turns = mutableListOf(Turn.DrawCard(
            p2,
            CardType.ATTACK
        )))
        assertFalse(validator.validateTurn(session, Turn.Nope(p1, 0)))
    }

    @Test
    fun `validateTurn Nope returns false when no NOPE card`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), turns = mutableListOf(Turn.Attack(p1)))
        assertFalse(validator.validateTurn(session, Turn.Nope(p1, 0)))
    }

    @Test
    fun `validateTurn Nope returns true when target is Attack`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1, CardType.NOPE to 1), turns = mutableListOf(Turn.Attack(p1)))
        assertTrue(validator.validateTurn(session, Turn.Nope(p1, 0)))
    }

    @Test
    fun `validateTurn Pass always returns false`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1))
        assertFalse(validator.validateTurn(session, Turn.Pass(p1)))
    }

    @Test
    fun `validateCardDistribution returns valid for correct distribution`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val p1Hand = mapOf(CardType.DEFUSE to 1, CardType.ATTACK to 1, CardType.SPECIAL_1 to 3, CardType.SKIP to 1, CardType.FAVOR to 1, CardType.SHUFFLE to 1)
        val p2Hand = mapOf(CardType.DEFUSE to 1, CardType.SKIP to 1, CardType.SPECIAL_2 to 3, CardType.NOPE to 1, CardType.ATTACK to 1, CardType.FAVOR to 1)

        val deck = DeckComposition.CARDS.toMutableMap()
        val remainingCards = mutableMapOf<CardType, Int>()
        deck.forEach { (type, total) ->
            val used = (p1Hand[type] ?: 0) + (p2Hand[type] ?: 0)
            remainingCards[type] = total - used
        }

        val session = GameSession(
            id = UUID.randomUUID(),
            participants = setOf(p1, p2),
            turns = mutableListOf(),
            discardPile = mutableMapOf(),
            drawPile = remainingCards.flatMap { (t, c) -> List(c) { t } }.toMutableList(),
            status = GameStatus.ACTIVE,
            whoseTurn = p1,
            playerHands = mutableMapOf(
                p1 to p1Hand.toMutableMap(),
                p2 to p2Hand.toMutableMap()
            )
        )

        val result = validator.validateCardDistribution(session, DeckComposition.CARDS, emptyMap())
        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `validateCardDistribution returns false when hand size not 8`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), p2Hand = mapOf(CardType.DEFUSE to 1))
        val result = validator.validateCardDistribution(session, DeckComposition.CARDS, emptyMap())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("8") })
    }

    @Test
    fun `validateCardDistribution returns false when no DEFUSE`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.ATTACK to 8), p2Hand = mapOf(CardType.ATTACK to 8))
        val result = validator.validateCardDistribution(session, DeckComposition.CARDS, emptyMap())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("DEFUSE") })
    }

    @Test
    fun `validateDrawPile returns false when total cards mismatch`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), p2Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf())
        val result = validator.validateDrawPile(session, DeckComposition.CARDS, emptyMap())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Total cards") })
    }

    @Test
    fun `validateDrawPile returns false when pool not empty`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val session = makeSession(p1, p2, whoseTurn = p1, p1Hand = mapOf(CardType.DEFUSE to 1), p2Hand = mapOf(CardType.DEFUSE to 1), drawPile = mutableListOf())
        val result = validator.validateDrawPile(session, DeckComposition.CARDS, mapOf(CardType.ATTACK to 1))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Pool is not empty") })
    }
}
