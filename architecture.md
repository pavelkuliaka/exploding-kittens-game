# Architecture 

## Enums

```mermaid
classDiagram
    class CardType {
        <<enumeration>>
        EXPLODING_KITTEN
        DEFUSE
        NOPE
        ATTACK
        SKIP
        SEE_THE_FUTURE
        SHUFFLE
        FAVOR
        SPECIAL_1
        SPECIAL_2
        SPECIAL_3
    }

    class ActionType {
        <<enumeration>>
        DRAW_CARD
        DEFUSE
        ATTACK
        SKIP
        SEE_THE_FUTURE
        SHUFFLE
        FAVOR
        PLAY_DOUBLE
        PLAY_TRIPLE
        NOPE
    }

    class GameStatus {
        <<enumeration>>
        ACTIVE
        FINISHED
    }
```

## Turn & Actions

```mermaid
classDiagram
    class Turn {
        <<sealed class>>
        #playerId: UUID

        DrawCard
        Defuse
        Nope
        Attack
        Skip
        SeeTheFuture
        Shuffle
        Favor
        Pass
        PlayDouble
        PlayTriple
    }

    class DrawCard {
        +card: CardType
    }

    class Defuse {
        +insertPosition: Int
    }

    class Nope {
        +targetTurnIndex: Int
    }

    class Attack {
    }

    class Skip {
    }

    class SeeTheFuture {
    }

    class Shuffle {
        +newDrawPile: List~CardType~
    }

    class Favor {
        +takenCard: CardType
    }

    class Pass {
    }

    class PlayDouble {
        +card: CardType
        +stolenCard: CardType
    }

    class PlayTriple {
        +card: CardType
    }

    Turn <|-- DrawCard
    Turn <|-- Defuse
    Turn <|-- Nope
    Turn <|-- Attack
    Turn <|-- Skip
    Turn <|-- SeeTheFuture
    Turn <|-- Shuffle
    Turn <|-- Favor
    Turn <|-- Pass
    Turn <|-- PlayDouble
    Turn <|-- PlayTriple

    DrawCard --> CardType : card
    Defuse --> CardType : EXPLODING_KITTEN
    Shuffle --> CardType : newDrawPile
    Favor --> CardType : takenCard
    PlayDouble --> CardType : card + stolenCard
    PlayTriple --> CardType : card
```

## Cards & Deck

```mermaid
classDiagram
    class CardType 

    class DeckComposition {
        <<object>>
        +CARDS: Map~CardType, Int~
        +TOTAL_CARDS: Int
    }

    DeckComposition --> CardType : composition
```

## Engine & Statistics

```mermaid
classDiagram
    class IGameAdminEngine {
        <<interface>>
        +gameRepository: IGameRepository
        +playerRepository: IPlayerRepository
        +ruleValidator: IRuleValidator
        +statisticsService: IStatisticsService
        +addTurn(sessionId: UUID, turn: Turn): Boolean
        +startNewSession(player1Id: UUID, player2Id: UUID): UUID
        +endSession(sessionId: UUID, winnerId: UUID?)
    }

    class GameAdminEngine {
        <<class>>
        -gameRepository: IGameRepository
        -playerRepository: IPlayerRepository
        -ruleValidator: IRuleValidator
        -statisticsService: IStatisticsService
        +addTurn(sessionId: UUID, turn: Turn): Boolean
        +startNewSession(player1Id: UUID, player2Id: UUID): UUID
        +endSession(sessionId: UUID, winnerId: UUID?)
        -processTurn(session: GameSession, turn: Turn)
        -handleNopeChain(session: GameSession)
        -spendCards(session: GameSession, turn: Turn)
        -isTurnNegated(turnIndex: Int, turns: List~Turn~): Boolean
        -isTurnInvalid(turnIndex: Int, turns: List~Turn~): Boolean
        -applyTurnEffect(session: GameSession, turn: Turn)
        -getOpponent(session: GameSession, playerId: UUID): UUID?
        -switchToOpponent(session: GameSession, playerId: UUID)
        -addToHand(session: GameSession, playerId: UUID, card: CardType, count: Int)
        -removeFromHand(session: GameSession, playerId: UUID, card: CardType, count: Int)
        -addToDiscard(session: GameSession, card: CardType, count: Int)
    }

    class IStatisticsService {
        <<interface>>
        +playerRepository: IPlayerRepository
        +getLeaderboard(number: UInt): List~Player~
    }

    class StatisticsService {
        <<class>>
        -playerRepository: IPlayerRepository
        +getLeaderboard(number: UInt): List~Player~
    }

    GameAdminEngine ..|> IGameAdminEngine
    StatisticsService ..|> IStatisticsService
    IGameAdminEngine --> IGameRepository : uses
    IGameAdminEngine --> IPlayerRepository : uses
    IGameAdminEngine --> IRuleValidator : uses
    IGameAdminEngine --> IStatisticsService : uses
    GameAdminEngine --> IGameRepository : uses
    GameAdminEngine --> IPlayerRepository : uses
    GameAdminEngine --> IRuleValidator : uses
    GameAdminEngine --> IStatisticsService : uses
    StatisticsService --> IPlayerRepository : uses
```

## Deck Composition

```mermaid
classDiagram
    class DeckComposition {
        <<object>>
        +CARDS: Map~CardType, Int~
        +TOTAL_CARDS: Int

        EXPLODING_KITTEN: 1
        DEFUSE: 3
        NOPE: 3
        ATTACK: 2
        SKIP: 3
        SEE_THE_FUTURE: 3
        SHUFFLE: 2
        FAVOR: 3
        SPECIAL_1: 4
        SPECIAL_2: 4
        SPECIAL_3: 4
    }

    DeckComposition --> CardType : references
```

## Players & Statistics

```mermaid
classDiagram
    class Player {
        <<data class>>
        +id: UUID
        +name: String
        +isPlaying: Boolean
        +stats: PlayerStats
    }

    class PlayerStats {
        <<data class>>
        +totalGames: Int
        +wins: Int
        +losses: Int
        +winRate: Int
        +defused: Int
    }

    Player --> PlayerStats : contains
```

## Repositories & JSON

```mermaid
classDiagram
    class IGameRepository {
        <<interface>>
        +addSession(session: GameSession)
        +getSession(sessionId: UUID): GameSession?
        +removeSession(sessionId: UUID)
        +saveSessions(): Boolean
        +loadSessions(): Boolean
    }

    class IPlayerRepository {
        <<interface>>
        +addPlayer(player: Player)
        +getAllPlayers(): List~Player~
        +getPlayer(playerId: UUID): Player?
        +savePlayers(): Boolean
        +loadPlayers(): Boolean
    }

    class JsonGameRepository {
        <<class>>
        -filePath: String
        -sessions: MutableMap~UUID, GameSession~
        +addSession(session: GameSession)
        +getSession(sessionId: UUID): GameSession?
        +removeSession(sessionId: UUID)
        +saveSessions(): Boolean
        +loadSessions(): Boolean
    }

    class JsonPlayerRepository {
        <<class>>
        -filePath: String
        -players: MutableMap~UUID, Player~
        +addPlayer(player: Player)
        +getAllPlayers(): List~Player~
        +getPlayer(playerId: UUID): Player?
        +savePlayers(): Boolean
        +loadPlayers(): Boolean
    }

    JsonGameRepository ..|> IGameRepository
    JsonPlayerRepository ..|> IPlayerRepository
```

## Rule Validation

```mermaid
classDiagram
    class IRuleValidator {
        <<interface>>
        +validateCardDistribution(session: GameSession, deckComposition: Map~CardType, Int~, availableCards: Map~CardType, Int~): ValidationResult
        +validateDrawPile(session: GameSession, deckComposition: Map~CardType, Int~, availableCards: Map~CardType, Int~): ValidationResult
        +validateTurn(gameSession: GameSession, nextTurn: Turn): Boolean
    }

    class RuleValidator {
        <<class>>
        +validateCardDistribution(session: GameSession, deckComposition: Map~CardType, Int~, availableCards: Map~CardType, Int~): ValidationResult
        +validateDrawPile(session: GameSession, deckComposition: Map~CardType, Int~, availableCards: Map~CardType, Int~): ValidationResult
        +validateTurn(gameSession: GameSession, nextTurn: Turn): Boolean
        -getOpponent(session: GameSession, playerId: UUID): UUID?
        -hasCard(session: GameSession, playerId: UUID, card: CardType): Boolean
        -isCatCard(card: CardType): Boolean
    }

    class ValidationResult {
        <<data class>>
        +isValid: Boolean
        +errors: List~String~
    }

    RuleValidator ..|> IRuleValidator
    IRuleValidator --> ValidationResult : returns
    IRuleValidator --> GameSession : validates
    IRuleValidator --> Turn : validates
```

## Session & State

```mermaid
classDiagram
    class GameSession {
        <<data class>>
        +id: UUID
        +participants: Set~UUID~
        +turns: MutableList~Turn~
        +discardPile: MutableMap~CardType, Int~
        +drawPile: MutableList~CardType~
        +status: GameStatus
        +whoseTurn: UUID?
        +playerHands: MutableMap~UUID, MutableMap~CardType, Int~~
        +initialState: GameStateSnapshot?
        +attackTurnsRemaining: Int
        +mustDefuse: Boolean
        +winnerId: UUID?
    }

    class GameStateSnapshot {
        <<data class>>
        +whoseTurn: UUID?
        +attackTurnsRemaining: Int
        +mustDefuse: Boolean
        +playerHands: Map~UUID, Map~CardType, Int~~
        +discardPile: Map~CardType, Int~
        +drawPile: List~CardType~
        +winnerId: UUID?
    }

    GameSession --> GameStatus : uses
    GameSession --> CardType : references
    GameSession --> Turn : contains
    GameSession o-- GameStateSnapshot : initialState
```