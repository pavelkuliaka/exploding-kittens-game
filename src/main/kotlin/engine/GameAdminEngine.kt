package com.github.pavelkuliaka.engine

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.GameSession
import com.github.pavelkuliaka.model.GameStatus
import com.github.pavelkuliaka.model.Turn
import com.github.pavelkuliaka.model.snapshot
import com.github.pavelkuliaka.model.restoreSnapshot
import com.github.pavelkuliaka.repository.IGameRepository
import com.github.pavelkuliaka.repository.IPlayerRepository
import com.github.pavelkuliaka.service.IStatisticsService
import com.github.pavelkuliaka.validation.IRuleValidator
import java.util.UUID

interface IGameAdminEngine {
    val gameRepository: IGameRepository
    val playerRepository: IPlayerRepository
    val ruleValidator: IRuleValidator
    val statisticsService: IStatisticsService
    fun addTurn(sessionId: UUID, turn: Turn) : Boolean
    fun startNewSession(player1Id: UUID, player2Id: UUID) : UUID
    fun endSession(sessionId: UUID, winnerId: UUID?)
}

class GameAdminEngine (
    override val gameRepository: IGameRepository,
    override val playerRepository: IPlayerRepository,
    override val ruleValidator: IRuleValidator,
    override val statisticsService: IStatisticsService,
) : IGameAdminEngine {

    override fun addTurn(sessionId: UUID, turn: Turn): Boolean {
        val session = gameRepository.getSession(sessionId) ?: return false

        if (!ruleValidator.validateTurn(session, turn)) return false

        if (session.initialState == null) {
            session.initialState = session.snapshot()
        }

        session.turns.add(turn)

        processTurn(session, turn)

        return true
    }

    private fun processTurn(session: GameSession, turn: Turn) {
        when (turn) {
            is Turn.DrawCard -> processDrawCard(session, turn)
            is Turn.Defuse -> processDefuse(session, turn)
            is Turn.Nope -> processNope(session, turn)
            is Turn.Attack -> processAttack(session, turn)
            is Turn.Skip -> processSkip(session, turn)
            is Turn.SeeTheFuture -> processSeeTheFuture(session, turn)
            is Turn.Shuffle -> processShuffle(session, turn)
            is Turn.Favor -> processFavor(session, turn)
            is Turn.PlayDouble -> processPlayDouble(session, turn)
            is Turn.PlayTriple -> processPlayTriple(session, turn)
            is Turn.Pass -> {}
        }

        if (turn is Turn.Nope) {
            handleNopeChain(session)
        }
    }

    private fun processDrawCard(session: GameSession, turn: Turn.DrawCard) {
        session.drawPile.removeAt(0)

        addToHand(session, turn.playerId, turn.card)

        if (turn.card == CardType.EXPLODING_KITTEN) {
            session.mustDefuse = true
        } else {
            if (session.attackTurnsRemaining > 0) {
                session.attackTurnsRemaining--
            }

            if (session.attackTurnsRemaining == 0) {
                switchToOpponent(session, turn.playerId)
            }
        }
    }

    private fun processDefuse(session: GameSession, turn: Turn.Defuse) {
        spendCards(session, turn)

        val position = turn.insertPosition.coerceAtMost(session.drawPile.size)
        session.drawPile.add(position, CardType.EXPLODING_KITTEN)

        session.mustDefuse = false
        switchToOpponent(session, turn.playerId)
    }

    private fun processNope(session: GameSession, turn: Turn.Nope) {
        spendCards(session, turn)
    }

    private fun processAttack(session: GameSession, turn: Turn.Attack) {
        spendCards(session, turn)

        val opponent = getOpponent(session, turn.playerId) ?: return

        session.attackTurnsRemaining += 2
        session.whoseTurn = opponent
    }

    private fun processSkip(session: GameSession, turn: Turn.Skip) {
        spendCards(session, turn)

        if (session.attackTurnsRemaining > 0) {
            session.attackTurnsRemaining--
        }

        if (session.attackTurnsRemaining == 0) {
            switchToOpponent(session, turn.playerId)
        }
    }

    private fun processSeeTheFuture(session: GameSession, turn: Turn.SeeTheFuture) {
        spendCards(session, turn)
    }

    private fun processShuffle(session: GameSession, turn: Turn.Shuffle) {
        spendCards(session, turn)

        session.drawPile.clear()
        session.drawPile.addAll(turn.newDrawPile)
    }

    private fun processFavor(session: GameSession, turn: Turn.Favor) {
        spendCards(session, turn)

        val opponent = getOpponent(session, turn.playerId) ?: return

        removeFromHand(session, opponent, turn.takenCard)
        addToHand(session, turn.playerId, turn.takenCard)
    }

    private fun processPlayDouble(session: GameSession, turn: Turn.PlayDouble) {
        spendCards(session, turn)

        val opponent = getOpponent(session, turn.playerId) ?: return

        removeFromHand(session, opponent, turn.stolenCard)
        addToHand(session, turn.playerId, turn.stolenCard)
    }

    private fun processPlayTriple(session: GameSession, turn: Turn.PlayTriple) {
        spendCards(session, turn)
    }

    private fun handleNopeChain(session: GameSession) {
        val initialState = session.initialState ?: return

        val turnsCopy = session.turns.toList()

        session.restoreSnapshot(initialState)
        session.turns.clear()

        for ((i, turn) in turnsCopy.withIndex()) {
            session.turns.add(turn)

            val negated = isTurnNegated(i, turnsCopy)
            val invalid = isTurnInvalid(i, turnsCopy)

            spendCards(session, turn)

            if (!negated && !invalid) {
                applyTurnEffect(session, turn)
            }
        }
    }

    private fun cardForTurn(turn: Turn): Pair<CardType, Int>? = when (turn) {
        is Turn.Defuse -> CardType.DEFUSE to 1
        is Turn.Nope -> CardType.NOPE to 1
        is Turn.Attack -> CardType.ATTACK to 1
        is Turn.Skip -> CardType.SKIP to 1
        is Turn.SeeTheFuture -> CardType.SEE_THE_FUTURE to 1
        is Turn.Shuffle -> CardType.SHUFFLE to 1
        is Turn.Favor -> CardType.FAVOR to 1
        is Turn.PlayDouble -> turn.card to 2
        is Turn.PlayTriple -> turn.card to 3
        else -> null
    }

    private fun spendCards(session: GameSession, turn: Turn) {
        val (card, count) = cardForTurn(turn) ?: return
        removeFromHand(session, turn.playerId, card, count)
        addToDiscard(session, card, count)
    }

    private fun isTurnInvalid(turnIndex: Int, turns: List<Turn>): Boolean {
        val turn = turns[turnIndex]
        if (turn !is Turn.Defuse) return false

        return turns.take(turnIndex).withIndex().none { (idx, prevTurn) ->
            prevTurn is Turn.DrawCard && prevTurn.card == CardType.EXPLODING_KITTEN &&
                prevTurn.playerId == turn.playerId && !isTurnNegated(idx, turns)
        }
    }

    private fun isTurnNegated(turnIndex: Int, turns: List<Turn>): Boolean {
        val nopes = turns.withIndex()
            .filter { (_, t) -> t is Turn.Nope && t.targetTurnIndex == turnIndex }
        val nopeCount = nopes
            .count { (idx, _) -> !isTurnNegated(idx, turns) }
        return nopeCount % 2 == 1
    }

    private fun advanceAfterTurn(session: GameSession, playerId: UUID) {
        if (session.attackTurnsRemaining > 0) {
            session.attackTurnsRemaining--
        }
        if (session.attackTurnsRemaining == 0) {
            switchToOpponent(session, playerId)
        }
    }

    private fun applyTurnEffect(session: GameSession, turn: Turn) {
        when (turn) {
            is Turn.DrawCard -> applyDrawCardEffect(session, turn)
            is Turn.Defuse -> applyDefuseEffect(session, turn)
            is Turn.Attack -> applyAttackEffect(session, turn)
            is Turn.Skip -> applySkipEffect(session, turn)
            is Turn.Shuffle -> applyShuffleEffect(session, turn)
            is Turn.Favor -> applyFavorEffect(session, turn)
            is Turn.PlayDouble -> applyPlayDoubleEffect(session, turn)
            else -> {}
        }
    }

    private fun applyDrawCardEffect(session: GameSession, turn: Turn.DrawCard) {
        session.drawPile.removeAt(0)
        addToHand(session, turn.playerId, turn.card)

        if (turn.card == CardType.EXPLODING_KITTEN) {
            session.mustDefuse = true
        } else {
            advanceAfterTurn(session, turn.playerId)
        }
    }

    private fun applyDefuseEffect(session: GameSession, turn: Turn.Defuse) {
        val position = turn.insertPosition.coerceAtMost(session.drawPile.size)
        session.drawPile.add(position, CardType.EXPLODING_KITTEN)

        session.mustDefuse = false
        switchToOpponent(session, turn.playerId)
    }

    private fun applyAttackEffect(session: GameSession, turn: Turn.Attack) {
        val opponent = getOpponent(session, turn.playerId) ?: return
        session.attackTurnsRemaining += 2
        session.whoseTurn = opponent
    }

    private fun applySkipEffect(session: GameSession, turn: Turn.Skip) {
        advanceAfterTurn(session, turn.playerId)
    }

    private fun applyShuffleEffect(session: GameSession, turn: Turn.Shuffle) {
        session.drawPile.clear()
        session.drawPile.addAll(turn.newDrawPile)
    }

    private fun applyFavorEffect(session: GameSession, turn: Turn.Favor) {
        val opponent = getOpponent(session, turn.playerId) ?: return
        removeFromHand(session, opponent, turn.takenCard)
        addToHand(session, turn.playerId, turn.takenCard)
    }

    private fun applyPlayDoubleEffect(session: GameSession, turn: Turn.PlayDouble) {
        val opponent = getOpponent(session, turn.playerId) ?: return
        removeFromHand(session, opponent, turn.stolenCard)
        addToHand(session, turn.playerId, turn.stolenCard)
    }

    private fun getOpponent(session: GameSession, playerId: UUID): UUID? {
        return session.participants.firstOrNull { it != playerId }
    }

    private fun switchToOpponent(session: GameSession, playerId: UUID) {
        val opponent = getOpponent(session, playerId)
        if (opponent != null) {
            session.whoseTurn = opponent
        }
    }

    private fun addToHand(session: GameSession, playerId: UUID, card: CardType, count: Int = 1) {
        val hand = session.playerHands.getOrPut(playerId) { mutableMapOf() }
        hand[card] = (hand[card] ?: 0) + count
    }

    private fun removeFromHand(session: GameSession, playerId: UUID, card: CardType, count: Int = 1) {
        val hand = session.playerHands[playerId] ?: return
        hand[card] = ((hand[card] ?: 0) - count).coerceAtLeast(0)
        if (hand[card] == 0) hand.remove(card)
    }

    private fun addToDiscard(session: GameSession, card: CardType, count: Int = 1) {
        session.discardPile[card] = (session.discardPile[card] ?: 0) + count
    }

    override fun startNewSession(player1Id: UUID, player2Id: UUID): UUID {
        val session =
            GameSession(
                id = UUID.randomUUID(),
                participants = setOf(player1Id, player2Id),
                turns = mutableListOf(),
                discardPile = mutableMapOf(),
                drawPile = mutableListOf(),
                status = GameStatus.ACTIVE,
                whoseTurn = player1Id
            )
        playerRepository.getPlayer(player1Id)?.isPlaying = true
        playerRepository.getPlayer(player2Id)?.isPlaying = true

        gameRepository.addSession(session)

        return session.id
    }

    override fun endSession(sessionId: UUID, winnerId: UUID?) {
        val session = gameRepository.getSession(sessionId) ?: return
        session.status = GameStatus.FINISHED
        if (winnerId != null) {
            session.winnerId = winnerId
        }
        for (playerId in session.participants) {
            val player = playerRepository.getPlayer(playerId) ?: return
            player.isPlaying = false
            player.stats.totalGames++
            if (playerId == winnerId) player.stats.wins++
            else if (winnerId != null) player.stats.losses++
            player.stats.winRate = player.stats.wins / player.stats.totalGames
        }
    }
}
