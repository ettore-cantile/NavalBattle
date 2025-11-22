package com.example.navalbattle.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// --- COSTANTI GLOBALI ---
const val GRID_SIZE = 5
private const val TAG = "GameViewModel"

class GameViewModel : ViewModel() {

    // ⚠️ IMPORTANTE: inizializzare PRIMA shipsToPlace
    private val shipsToPlace = listOf(2, 1, 1) // Navi del gioco

    // ⚠️ VA DOPO shipsToPlace per evitare il crash
    private val _gameState = MutableStateFlow(createInitialGameState())
    val gameState = _gameState.asStateFlow()

    private var placementIndex = 0
    private var currentShipOrientation = ShipOrientation.HORIZONTAL

    init {
        Log.d(TAG, "ViewModel Inizializzato.")
        printGridState("Griglia Iniziale Computer", _gameState.value.player2.grid)
    }

    // =================================================================================
    // --- EVENTI UI ---
    // =================================================================================

    fun onCellClick(row: Int, col: Int) {
        val currentState = _gameState.value
        Log.d(TAG, "onCellClick: ($row, $col) - fase ${currentState.phase}")

        when (currentState.phase) {
            GamePhase.PLACEMENT -> placeShipForHuman(row, col)
            GamePhase.BATTLE -> if (!currentState.currentPlayer.isComputer) handleHumanAttack(row, col)
            GamePhase.FINISHED -> {}
        }
    }

    fun rotateShip() {
        if (_gameState.value.phase == GamePhase.PLACEMENT) {
            currentShipOrientation = if (currentShipOrientation == ShipOrientation.HORIZONTAL)
                ShipOrientation.VERTICAL else ShipOrientation.HORIZONTAL
        }
    }

    fun resetGame() {
        placementIndex = 0
        currentShipOrientation = ShipOrientation.HORIZONTAL
        _gameState.value = createInitialGameState()
        printGridState("Reset Griglia Computer", _gameState.value.player2.grid)
    }

    fun updatePlacementPreview(row: Int, col: Int) {
        if (_gameState.value.phase != GamePhase.PLACEMENT || placementIndex >= shipsToPlace.size) {
            // Se l'anteprima non è necessaria, assicurati che sia null
            if (_gameState.value.placementPreview != null) {
                _gameState.update { it.copy(placementPreview = null) }
            }
            return
        }

        val shipSize = shipsToPlace[placementIndex]
        val isHorizontal = currentShipOrientation == ShipOrientation.HORIZONTAL
        val grid = _gameState.value.player1.grid

        val previewCoordinates = (0 until shipSize).map { i ->
            val r = if (isHorizontal) row else row + i
            val c = if (isHorizontal) col + i else col
            r to c
        }

        // Controlla se le coordinate sono valide per il piazzamento
        val isValid = previewCoordinates.all { (r, c) ->
            canPlaceShip(grid, r, c, 1, false) // Controlla cella per cella
        }

        _gameState.update {
            it.copy(placementPreview = PlacementPreview(previewCoordinates, isValid))
        }
    }

    /**
     * Rimuove l'anteprima (es. quando il cursore esce dalla griglia).
     */
    fun clearPlacementPreview() {
        if (_gameState.value.placementPreview != null) {
            _gameState.update { it.copy(placementPreview = null) }
        }
    }

    // =================================================================================
    // --- PIAZZAMENTO UMANO ---
    // =================================================================================

    private fun placeShipForHuman(row: Int, col: Int) {
        if (placementIndex >= shipsToPlace.size) return

        val shipSize = shipsToPlace[placementIndex]
        val player = _gameState.value.player1
        val grid = player.grid.map { it.toMutableList() }.toMutableList()

        val isHorizontal = currentShipOrientation == ShipOrientation.HORIZONTAL

        if (canPlaceShip(grid, row, col, shipSize, isHorizontal)) {
            val coords = mutableListOf<Pair<Int, Int>>()

            for (i in 0 until shipSize) {
                val r = if (isHorizontal) row else row + i
                val c = if (isHorizontal) col + i else col
                grid[r][c] = grid[r][c].copy(status = CellStatus.SHIP)
                coords += r to c
            }

            val ship = Ship(shipSize, coords, currentShipOrientation)
            val updatedPlayer = player.copy(
                grid = grid.map { it.toList() },
                ships = player.ships + ship
            )

            _gameState.update { it.copy(player1 = updatedPlayer) }
            placementIndex++

            if (placementIndex >= shipsToPlace.size) {
                _gameState.update { it.copy(phase = GamePhase.BATTLE) }
            }
        }
    }

    // =================================================================================
    // --- ATTACCO GIOCATORE ---
    // =================================================================================

    private fun handleHumanAttack(row: Int, col: Int) {
        val state = _gameState.value
        val computer = state.player2
        val cell = computer.grid.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.status == CellStatus.HIT || cell.status == CellStatus.MISS) return

        val newStatus = if (cell.status == CellStatus.SHIP) CellStatus.HIT else CellStatus.MISS

        val newGrid = computer.grid.map { it.toMutableList() }.toMutableList()
        newGrid[row][col] = cell.copy(status = newStatus)
        val updatedComputer = computer.copy(grid = newGrid.map { it.toList() })

        var sunkMessage: String? = null

        if (newStatus == CellStatus.HIT) {
            val hitShip = updatedComputer.ships.find { it.coordinates.contains(row to col) }
            if (hitShip != null && isSunk(hitShip, updatedComputer.grid)) {
                sunkMessage = "Enemy ship sunk!"
            }
        }

        val winner = checkForWinner(state.player1, updatedComputer)

        _gameState.update {
            it.copy(
                player2 = updatedComputer,
                winner = winner,
                phase = if (winner != null) GamePhase.FINISHED else it.phase,
                sunkMessage = sunkMessage
            )
        }

        if (winner == null) switchTurn()
    }

    // =================================================================================
    // --- TURNO COMPUTER ---
    // =================================================================================

    private suspend fun computerTurn() {
        delay(Random.nextLong(800, 2000))
        _gameState.update { it.copy(sunkMessage = null) }

        val human = _gameState.value.player1
        val validCells = buildList {
            for (r in 0 until GRID_SIZE)
                for (c in 0 until GRID_SIZE)
                    if (human.grid[r][c].status !in listOf(CellStatus.HIT, CellStatus.MISS))
                        add(r to c)
        }

        if (validCells.isEmpty()) return

        val (r, c) = validCells.random()
        val cell = human.grid[r][c]
        val newStatus = if (cell.status == CellStatus.SHIP) CellStatus.HIT else CellStatus.MISS

        val newGrid = human.grid.map { it.toMutableList() }.toMutableList()
        newGrid[r][c] = cell.copy(status = newStatus)
        val updatedHuman = human.copy(grid = newGrid.map { it.toList() })

        val winner = checkForWinner(updatedHuman, _gameState.value.player2)

        _gameState.update { it.copy(player1 = updatedHuman, winner = winner) }

        if (winner == null) switchTurn()
    }

    private fun switchTurn() {
        val next = if (_gameState.value.currentPlayer.isComputer)
            _gameState.value.player1 else _gameState.value.player2

        _gameState.update { it.copy(currentPlayer = next) }

        if (next.isComputer) viewModelScope.launch { computerTurn() }
    }

    // =================================================================================
    // --- CREAZIONE STATO GIOCO ---
    // =================================================================================

    private fun createInitialGameState(): GameState {
        val human = Player(createEmptyGrid(), emptyList(), isComputer = false)
        val computer = createComputerPlayer()
        return GameState(human, computer, human)
    }

    private fun createComputerPlayer(): Player {
        val grid = createEmptyGrid().map { it.toMutableList() }.toMutableList()
        val ships = placeShipsRandomly(grid)
        return Player(grid.map { it.toList() }, ships, isComputer = true)
    }

    private fun placeShipsRandomly(grid: MutableList<MutableList<Cell>>): List<Ship> {
        val ships = mutableListOf<Ship>()
        val maxAttempts = 100

        shipsToPlace.forEach { size ->
            var placed = false
            var attempts = 0

            while (!placed && attempts < maxAttempts) {
                val row = Random.nextInt(GRID_SIZE)
                val col = Random.nextInt(GRID_SIZE)
                val horizontal = Random.nextBoolean()

                if (canPlaceShip(grid, row, col, size, horizontal)) {
                    val coords = (0 until size).map { i ->
                        val r = if (horizontal) row else row + i
                        val c = if (horizontal) col + i else col
                        grid[r][c] = grid[r][c].copy(status = CellStatus.SHIP)
                        r to c
                    }

                    ships += Ship(size, coords, if (horizontal) ShipOrientation.HORIZONTAL else ShipOrientation.VERTICAL)
                    placed = true
                }

                attempts++
            }

            if (!placed) {
                // reset e riprova
                grid.forEachIndexed { r, rowList ->
                    rowList.forEachIndexed { c, _ ->
                        grid[r][c] = grid[r][c].copy(status = CellStatus.EMPTY)
                    }
                }
                return placeShipsRandomly(grid)
            }
        }

        return ships
    }

    // =================================================================================
    // --- SUPPORTO ---
    // =================================================================================

    private fun canPlaceShip(grid: List<List<Cell>>, row: Int, col: Int, size: Int, horizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val r = if (horizontal) row else row + i
            val c = if (horizontal) col + i else col
            if (r !in 0 until GRID_SIZE || c !in 0 until GRID_SIZE || grid[r][c].status != CellStatus.EMPTY)
                return false
        }
        return true
    }

    private fun createEmptyGrid(): List<List<Cell>> =
        List(GRID_SIZE) { r -> List(GRID_SIZE) { c -> Cell(r, c, CellStatus.EMPTY) } }

    private fun checkForWinner(p1: Player, p2: Player): Player? {
        if (p2.ships.all { isSunk(it, p2.grid) }) return p1
        if (p1.ships.all { isSunk(it, p1.grid) }) return p2
        return null
    }

    private fun isSunk(ship: Ship, grid: List<List<Cell>>) =
        ship.coordinates.all { (r, c) -> grid[r][c].status == CellStatus.HIT }

    private fun printGridState(label: String, grid: List<List<Cell>>) {
        Log.d(TAG, "--- $label ---")
        val str = grid.joinToString("\n") { row ->
            row.joinToString(" ") { cell ->
                when (cell.status) {
                    CellStatus.EMPTY -> "🌊"
                    CellStatus.SHIP -> "🚢"
                    CellStatus.HIT -> "💥"
                    CellStatus.MISS -> "💀"
                }
            }
        }
        Log.d(TAG, "\n$str")
    }
}
