package com.example.navalbattle.game

/**
 * Gestisce le fasi principali del gioco.
 */
enum class GamePhase {
    PLACEMENT,
    BATTLE,// Fase di piazzamento navi del giocatore    // Fase di combattimento
    FINISHED   // Fase di fine partita
} // ---> ERRORE [RISOLTO]: La parentesi graffa '{' è stata rimossa da dopo 'FINISHED'.

/**
 * Stato di una singola cella della griglia.
 */
enum class CellStatus {
    EMPTY, // Acqua
    SHIP,  // Parte di una nave non colpita
    HIT,   // Parte di una nave colpita
    MISS   // Colpo a vuoto (acqua)
}

/**
 * Orientamento di una nave (utile per la UI).
 */
enum class ShipOrientation {
    HORIZONTAL,
    VERTICAL
}

/**
 * Rappresenta una singola cella nella griglia.
 */
data class Cell(val row: Int, val col: Int, val status: CellStatus)

/**
 * Rappresenta una nave con la sua dimensione, coordinate e orientamento.
 */
data class Ship(
    val size: Int,
    val coordinates: List<Pair<Int, Int>>,
    val orientation: ShipOrientation
)

/**
 * Rappresenta un giocatore, con la sua griglia, navi e se è il computer.
 */
data class Player(
    val grid: List<List<Cell>>,
    val ships: List<Ship>,
    val isComputer: Boolean = false
)

/**
 * Rappresenta l'anteprima di una nave durante il piazzamento.
 * @param coordinates Le coordinate che la nave occuperebbe.
 * @param isValid Se la posizione è valida o no (per cambiare colore).
 */
// in GameModel.kt

data class PlacementPreview(
    val coordinates: List<Pair<Int, Int>>,
    val isValid: Boolean,
    val orientation: ShipOrientation
)


/**
 * Rappresenta l'intero stato del gioco in un dato momento.
 * È immutabile per una gestione dello stato più sicura.
 */
data class GameState(
    val player1: Player, // Giocatore umano
    val player2: Player, // Computer
    val currentPlayer: Player,
    val phase: GamePhase = GamePhase.PLACEMENT,
    val winner: Player? = null,
    val sunkMessage: String? = null, // Messaggio per "Nave affondata!"
    val placementPreview: PlacementPreview? = null,// Contiene le info per l'anteprima
    val isComputerThinking: Boolean = false,
    val draggedShipSize: Int? = null
)
