package com.github.pavelkuliaka.model

enum class CardType {
    EXPLODING_KITTEN, // 1
    DEFUSE, // 3
    NOPE, // 3
    ATTACK, // 2
    SKIP, // 3
    SEE_THE_FUTURE, // 3
    SHUFFLE, // 2
    FAVOR, // 3
    SPECIAL_1, // 4
    SPECIAL_2, // 4
    SPECIAL_3 // 4
}

enum class ActionType {
    DRAW_CARD,
    DEFUSE,
    ATTACK,
    SKIP,
    SEE_THE_FUTURE,
    SHUFFLE,
    FAVOR,
    PLAY_DOUBLE,
    PLAY_TRIPLE,
    NOPE
}

object DeckComposition {
    val CARDS: Map<CardType, Int> = mapOf(
        CardType.EXPLODING_KITTEN to 1,
        CardType.DEFUSE to 3,
        CardType.NOPE to 3,
        CardType.ATTACK to 2,
        CardType.SKIP to 3,
        CardType.SEE_THE_FUTURE to 3,
        CardType.SHUFFLE to 2,
        CardType.FAVOR to 3,
        CardType.SPECIAL_1 to 4,
        CardType.SPECIAL_2 to 4,
        CardType.SPECIAL_3 to 4,
    )
    val TOTAL_CARDS: Int = CARDS.values.sum()
}

enum class GameStatus {
    ACTIVE,
    FINISHED
}
