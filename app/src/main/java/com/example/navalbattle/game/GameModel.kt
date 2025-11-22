package com.example.navalbattle.game

enum class GamePhase {
    PLACEMENT,
    BATTLE,
    FINISHED
}

data class Cell(val row: Int, val col: Int, val status: CellStatus)

enum class CellStatus {
    EMPTY,
    SHIP,
    HIT,
    MISS
}
enum class ShipOrientation {
    HORIZONTAL,
    VERTICAL
}

data class Ship(
    val size: Int,
    val coordinates: List<Pair<Int, Int>>,
    val orientation: ShipOrientation
)
data class Player(
    val grid: List<List<Cell>>,
    val ships: List<Ship>,
    val isComputer: Boolean = false
)

data class GameState(
    val player1: Player,
    val player2: Player,
    val currentPlayer: Player,
    val phase: GamePhase = GamePhase.PLACEMENT,
    val winner: Player? = null,
    val sunkMessage: String? = null
)
