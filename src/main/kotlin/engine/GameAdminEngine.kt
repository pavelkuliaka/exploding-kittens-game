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
        removeFromHand(session, turn.playerId, CardType.DEFUSE)
        addToDiscard(session, CardType.DEFUSE)

        val position = turn.insertPosition.coerceAtMost(session.drawPile.size)
        session.drawPile.add(position, CardType.EXPLODING_KITTEN)

        session.mustDefuse = false
        switchToOpponent(session, turn.playerId)
    }

    private fun processNope(session: GameSession, turn: Turn.Nope) {
        removeFromHand(session, turn.playerId, CardType.NOPE)
        addToDiscard(session, CardType.NOPE)
    }

    private fun processAttack(session: GameSession, turn: Turn.Attack) {
        removeFromHand(session, turn.playerId, CardType.ATTACK)
        addToDiscard(session, CardType.ATTACK)

        val opponent = getOpponent(session, turn.playerId) ?: return

        session.attackTurnsRemaining += 2
        session.whoseTurn = opponent
    }

    private fun processSkip(session: GameSession, turn: Turn.Skip) {
        removeFromHand(session, turn.playerId, CardType.SKIP)
        addToDiscard(session, CardType.SKIP)

        if (session.attackTurnsRemaining > 0) {
            session.attackTurnsRemaining--
        }

        if (session.attackTurnsRemaining == 0) {
            switchToOpponent(session, turn.playerId)
        }
    }

    private fun processSeeTheFuture(session: GameSession, turn: Turn.SeeTheFuture) {
        removeFromHand(session, turn.playerId, CardType.SEE_THE_FUTURE)
        addToDiscard(session, CardType.SEE_THE_FUTURE)
    }

    private fun processShuffle(session: GameSession, turn: Turn.Shuffle) {
        removeFromHand(session, turn.playerId, CardType.SHUFFLE)
        addToDiscard(session, CardType.SHUFFLE)

        session.drawPile.clear()
        session.drawPile.addAll(turn.newDrawPile)
    }

    private fun processFavor(session: GameSession, turn: Turn.Favor) {
        removeFromHand(session, turn.playerId, CardType.FAVOR)
        addToDiscard(session, CardType.FAVOR)

        val opponent = getOpponent(session, turn.playerId) ?: return

        removeFromHand(session, opponent, turn.takenCard)
        addToHand(session, turn.playerId, turn.takenCard)
    }

    private fun processPlayDouble(session: GameSession, turn: Turn.PlayDouble) {
        removeFromHand(session, turn.playerId, turn.card, 2)
        addToDiscard(session, turn.card, 2)

        val opponent = getOpponent(session, turn.playerId) ?: return

        removeFromHand(session, opponent, turn.stolenCard)
        addToHand(session, turn.playerId, turn.stolenCard)
    }

    private fun processPlayTriple(session: GameSession, turn: Turn.PlayTriple) {
        removeFromHand(session, turn.playerId, turn.card, 3)
        addToDiscard(session, turn.card, 3)
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

    private fun spendCards(session: GameSession, turn: Turn) {
        when (turn) {
            is Turn.Defuse -> {
                removeFromHand(session, turn.playerId, CardType.DEFUSE)
                addToDiscard(session, CardType.DEFUSE)
            }
            is Turn.Nope -> {
                removeFromHand(session, turn.playerId, CardType.NOPE)
                addToDiscard(session, CardType.NOPE)
            }
            is Turn.Attack -> {
                removeFromHand(session, turn.playerId, CardType.ATTACK)
                addToDiscard(session, CardType.ATTACK)
            }
            is Turn.Skip -> {
                removeFromHand(session, turn.playerId, CardType.SKIP)
                addToDiscard(session, CardType.SKIP)
            }
            is Turn.SeeTheFuture -> {
                removeFromHand(session, turn.playerId, CardType.SEE_THE_FUTURE)
                addToDiscard(session, CardType.SEE_THE_FUTURE)
            }
            is Turn.Shuffle -> {
                removeFromHand(session, turn.playerId, CardType.SHUFFLE)
                addToDiscard(session, CardType.SHUFFLE)
            }
            is Turn.Favor -> {
                removeFromHand(session, turn.playerId, CardType.FAVOR)
                addToDiscard(session, CardType.FAVOR)
            }
            is Turn.PlayDouble -> {
                removeFromHand(session, turn.playerId, turn.card, 2)
                addToDiscard(session, turn.card, 2)
            }
            is Turn.PlayTriple -> {
                removeFromHand(session, turn.playerId, turn.card, 3)
                addToDiscard(session, turn.card, 3)
            }
            is Turn.DrawCard -> {}
            is Turn.Pass -> {}
        }
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

    private fun applyTurnEffect(session: GameSession, turn: Turn) {
        when (turn) {
            is Turn.DrawCard -> {
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
            is Turn.Defuse -> {
                val position = turn.insertPosition.coerceAtMost(session.drawPile.size)
                session.drawPile.add(position, CardType.EXPLODING_KITTEN)

                session.mustDefuse = false
                switchToOpponent(session, turn.playerId)
            }
            is Turn.Nope -> {}
            is Turn.Attack -> {
                val opponent = getOpponent(session, turn.playerId) ?: return
                session.attackTurnsRemaining += 2
                session.whoseTurn = opponent
            }
            is Turn.Skip -> {
                if (session.attackTurnsRemaining > 0) {
                    session.attackTurnsRemaining--
                }
                if (session.attackTurnsRemaining == 0) {
                    switchToOpponent(session, turn.playerId)
                }
            }
            is Turn.SeeTheFuture -> {}
            is Turn.Shuffle -> {
                session.drawPile.clear()
                session.drawPile.addAll(turn.newDrawPile)
            }
            is Turn.Favor -> {
                val opponent = getOpponent(session, turn.playerId) ?: return
                removeFromHand(session, opponent, turn.takenCard)
                addToHand(session, turn.playerId, turn.takenCard)
            }
            is Turn.Pass -> {}
            is Turn.PlayDouble -> {
                val opponent = getOpponent(session, turn.playerId) ?: return
                removeFromHand(session, opponent, turn.stolenCard)
                addToHand(session, turn.playerId, turn.stolenCard)
            }
            is Turn.PlayTriple -> {}
        }
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
