@file:OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)

package com.example.navalbattle.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navalbattle.game.*

@Composable
fun NavalBattleScreen(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00008B)) // Sfondo blu scuro
    ) {
        when (gameState.phase) {
            GamePhase.PLACEMENT -> PlacementPhase(gameViewModel)
            GamePhase.BATTLE -> BattlePhase(gameViewModel)
            GamePhase.FINISHED -> FinishedPhase(gameViewModel)
        }
    }
}

// ------------------------ FASI DEL GIOCO ------------------------

@Composable
fun PlacementPhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Place Your Ships", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Click on the grid to place your ships.", fontSize = 16.sp, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { gameViewModel.rotateShip() }) {
            Text("Rotate Ship")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Refresh, contentDescription = "Rotate Ship")
        }
        Spacer(Modifier.height(16.dp))
        GameBoard(
            player = gameState.player1,
            onCellClick = { row, col -> gameViewModel.onCellClick(row, col) },
            isOpponentBoard = false,
            phase = GamePhase.PLACEMENT,
            gameViewModel = gameViewModel // Passa il ViewModel per l'anteprima
        )
    }
}

@Composable
fun BattlePhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // --- SEZIONE GRIGLIA NEMICA ---
        Text("Enemy Grid", fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        EnemyBoardWithOverlay(
            gameState = gameState,
            onCellClick = { row, col -> gameViewModel.onCellClick(row, col) }
        )

        // --- TESTO DEL TURNO ---
        val turnText = if (gameState.currentPlayer == gameState.player1) "Your Turn" else "Computer is thinking..."
        Text(turnText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // --- GRIGLIA DEL GIOCATORE ---
        Text("Your Grid", fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        GameBoard(
            player = gameState.player1,
            onCellClick = { _, _ -> },
            isOpponentBoard = false,
            phase = GamePhase.BATTLE
        )
    }
}

@Composable
fun FinishedPhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val winnerText = if (gameState.winner == gameState.player1) "You Win!" else "Computer Wins!"
        Text(winnerText, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { gameViewModel.resetGame() }) {
            Text("Play Again")
        }
    }
}

// ------------------------ COMPONENTI UI RIUTILIZZABILI ------------------------

@Composable
private fun EnemyBoardWithOverlay(gameState: GameState, onCellClick: (Int, Int) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        GameBoard(
            player = gameState.player2,
            onCellClick = onCellClick,
            isOpponentBoard = true,
            phase = GamePhase.BATTLE
        )
        // --- CORREZIONE 2: Animazione "SHIP SUNK!" ---
        // L'animazione ora viene mostrata correttamente perché è nel Box più esterno,
        // che si occupa di sovrapporre gli elementi.
        AnimatedVisibility(
            visible = gameState.sunkMessage != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Text(
                text = gameState.sunkMessage ?: "",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameBoard(
    player: Player,
    onCellClick: (Int, Int) -> Unit,
    isOpponentBoard: Boolean,
    phase: GamePhase,
    gameViewModel: GameViewModel? = null // ViewModel opzionale per l'anteprima
) {
    val cellSize = 48.dp
    val gridSize = 5
    val gameState by gameViewModel?.gameState?.collectAsState() ?: remember { mutableStateOf(null) }

    Box(
        modifier = Modifier
            .width(cellSize * gridSize)
            .height(cellSize * gridSize)
            .border(1.dp, Color.White)
            .pointerInput(phase, gameViewModel) {
                if (phase == GamePhase.PLACEMENT && gameViewModel != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.first().position

                            val col = (position.x / size.width * gridSize).toInt().coerceIn(0, gridSize - 1)
                            val row = (position.y / size.height * gridSize).toInt().coerceIn(0, gridSize - 1)

                            gameViewModel.updatePlacementPreview(row, col)

                            if (event.type == PointerEventType.Exit) {
                                gameViewModel.clearPlacementPreview()
                            }
                        }
                    }
                }
            }
    ) {
        // --- CORREZIONE 1: Riorganizzazione dei Layer ---

        // Layer 1: Cella di base (solo sfondo e bordi)
        Column {
            (0 until gridSize).forEach { row ->
                Row {
                    (0 until gridSize).forEach { col ->
                        // GridCell ora disegna solo lo sfondo e gestisce il click
                        GridCell(player.grid[row][col], onCellClick, isOpponentBoard, phase, cellSize)
                    }
                }
            }
        }

        // Layer 2: Disegno delle navi del giocatore (se visibili)
        if (!isOpponentBoard) {
            player.ships.forEach { ship ->
                ShipDrawing(ship, cellSize)
            }
        }

        // Layer 3: Anteprima di piazzamento (se in fase di piazzamento)
        if (phase == GamePhase.PLACEMENT && !isOpponentBoard) {
            gameState?.placementPreview?.let { preview ->
                PlacementPreviewDrawing(preview, cellSize)
            }
        }

        // Layer 4: Indicatori di Colpito/Mancato (disegnati sopra tutto il resto)
        Column {
            player.grid.forEach { rowOfCells ->
                Row {
                    rowOfCells.forEach { cell ->
                        // HitMissMarker disegna solo se la cella è HIT o MISS
                        HitMissMarker(cell, cellSize)
                    }
                }
            }
        }
    }
}


@Composable
private fun PlacementPreviewDrawing(preview: PlacementPreview, cellSize: Dp) {
    val color = if (preview.isValid) Color.Green.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f)

    preview.coordinates.forEach { (row, col) ->
        Box(
            modifier = Modifier
                .offset(x = cellSize * col, y = cellSize * row)
                .size(cellSize)
                .background(color)
        )
    }
}

@Composable
private fun GridCell(cell: Cell, onCellClick: (Int, Int) -> Unit, isOpponentBoard: Boolean, phase: GamePhase, cellSize: Dp) {
    val isClickable = (phase == GamePhase.PLACEMENT && !isOpponentBoard) ||
            (phase == GamePhase.BATTLE && isOpponentBoard && cell.status != CellStatus.HIT && cell.status != CellStatus.MISS)
    Box(
        modifier = Modifier
            .size(cellSize)
            .background(Color.Blue.copy(alpha = 0.5f))
            .border(0.5.dp, Color.White.copy(alpha = 0.3f))
            .clickable(enabled = isClickable) { onCellClick(cell.row, cell.col) }
    ) {
        // Ora questo Box è vuoto, i marker sono gestiti separatamente
    }
}

// ---> NUOVO COMPOSABLE PER I MARKER <---
@Composable
fun HitMissMarker(cell: Cell, cellSize: Dp) {
    Box(
        modifier = Modifier.size(cellSize),
        contentAlignment = Alignment.Center
    ) {
        val marker = when (cell.status) {
            CellStatus.HIT -> "💥"
            CellStatus.MISS -> "💀"
            else -> "" // Non disegna nulla se la cella è vuota o solo una nave
        }
        if (marker.isNotEmpty()) {
            Text(marker, fontSize = 24.sp)
        }
    }
}


@Composable
private fun ShipDrawing(ship: Ship, cellSize: Dp) {
    val (minRow, minCol) = ship.coordinates.minWithOrNull(compareBy({ it.first }, { it.second })) ?: return
    val isHorizontal = ship.orientation == ShipOrientation.HORIZONTAL
    val shipWidth = if (isHorizontal) cellSize * ship.size else cellSize
    val shipHeight = if (isHorizontal) cellSize else cellSize * ship.size
    val shipIcon = if (ship.size == 1) "🚢" else "🛳️"
    Box(
        modifier = Modifier
            .offset(x = cellSize * minCol, y = cellSize * minRow)
            .size(width = shipWidth, height = shipHeight)
            .background(Color.DarkGray.copy(alpha = 0.7f))
            .border(1.dp, Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            shipIcon,
            fontSize = if (ship.size == 1) 24.sp else 36.sp,
            modifier = Modifier.rotate(if (isHorizontal) 0f else 90f)
        )
    }
}
