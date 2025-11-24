@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalAnimationApi::class)

package com.example.navalbattle.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navalbattle.game.*
import kotlin.math.roundToInt

// ---------- configurazione ----------
private val CELL_SIZE: Dp = 64.dp
private const val BOARD_GRID = 5

// Palette semplice
private val BG = Color(0xFF0B4C78)
private val BOARD_BG = Color(0xFFBFEAF5)
private val PREVIEW_OK = Color(0x8032CD32)    // verde semi
private val PREVIEW_BAD = Color(0x80FF5252)   // rosso semi
private val SHIP_COLOR = Color(0xFF444444)

// ------------------ NavalBattleScreen (entry) ------------------
@Composable
fun NavalBattleScreen(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(12.dp)
    ) {
        when (gameState.phase) {
            GamePhase.PLACEMENT -> PlacementPhase(gameViewModel)
            GamePhase.BATTLE -> BattlePhase(gameViewModel)
            GamePhase.FINISHED -> FinishedPhase(gameViewModel)
        }
    }
}

// ------------------ PlacementPhase ------------------
@Composable
fun PlacementPhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()
    val density = LocalDensity.current
    // definisci le navi da piazzare (ordine: prima la da 2 poi le singole)
    val shipsToPlace = listOf(2, 1, 1)
    val placedCount = gameState.player1.ships.size // quante sono già piazzate

    // Board metrics raccolte dalla GameBoard
    var boardOffset by remember { mutableStateOf(Offset.Unspecified) }
    var boardCellPx by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Place your ships", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // board con callback che restituisce offset e dimensione cella
        GameBoard(
            player = gameState.player1,
            onCellClick = { r, c -> gameViewModel.onCellClick(r, c) }, // click fallback
            isOpponentBoard = false,
            phase = GamePhase.PLACEMENT,
            gameViewModel = gameViewModel,
            onBoardMetricsChanged = { offset, cellPx, _ ->
                boardOffset = offset
                boardCellPx = cellPx
            }
        )

        // controlli (rotazione/reset)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { gameViewModel.rotateShip() }, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Rotate")
                Spacer(Modifier.width(8.dp))
                Text("Rotate")
            }
            Button(onClick = { gameViewModel.resetGame() }, modifier = Modifier.padding(8.dp)) {
                Text("Reset")
            }
        }

        // ship dock (solo la prossima nave è trascinabile)
        ShipDock(
            shipsToPlace = shipsToPlace,
            placedCount = placedCount,
            onDragStart = { /* nothing to forward to VM, UI handles preview */ },
            onDragEnd = { gameViewModel.clearPlacementPreview() },
            onDragCell = { r, c -> gameViewModel.updatePlacementPreview(r, c) },
            onDrop = { r, c ->
                // piazza richiamando onCellClick (la tua logica gestisce placeShipForHuman)
                gameViewModel.onCellClick(r, c)
                gameViewModel.clearPlacementPreview()
            },
            onDragExit = { gameViewModel.clearPlacementPreview() },
            boardOffset = boardOffset,
            boardCellPx = boardCellPx,
            nextDraggableIndex = placedCount
        )
    }
}

// ------------------ ShipDock + Draggable ------------------
@Composable
fun ShipDock(
    shipsToPlace: List<Int>,
    placedCount: Int,
    onDragStart: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCell: (Int, Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
    onDragExit: () -> Unit,
    boardOffset: Offset,
    boardCellPx: Float,
    nextDraggableIndex: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        shipsToPlace.forEachIndexed { index, size ->

            val isPlaced = index < placedCount

            when {
                isPlaced -> {
                    Spacer(modifier = Modifier.size(CELL_SIZE * size, CELL_SIZE))
                }

                else -> {
                    // 🔥 ADESSO TUTTE LE NAVI NON ANCORA PIAZZATE SONO TRASCINABILI
                    DraggableShipItem(
                        id = index,
                        size = size,
                        onDragStart = { onDragStart(index) },
                        onDragCell = { r, c -> onDragCell(r, c) },
                        onDrop = { r, c -> onDrop(r, c) },
                        onDragEnd = { onDragEnd() },
                        onDragExit = { onDragExit() },
                        boardOffset = boardOffset,
                        boardCellPx = boardCellPx
                    )
                }
            }
        }
    }
}

@Composable
fun StaticShipItem(size: Int) {
    // disegno identico a ShipDrawing per coerenza: usa stessa icona
    Box(
        modifier = Modifier
            .size(CELL_SIZE * size, CELL_SIZE)
            .background(SHIP_COLOR, RoundedCornerShape(8.dp))
            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(if (size > 1) "🛳️" else "🚤", fontSize = if (size > 1) 26.sp else 20.sp)
    }
}

@Composable
fun DraggableShipItem(
    id: Int,
    size: Int,
    onDragStart: () -> Unit,
    onDragCell: (Int, Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragExit: () -> Unit,
    boardOffset: Offset,
    boardCellPx: Float
) {
    // posizione visiva della "mini-nave" mentre trascini (solo UI)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // posizione assoluta (window) del box da cui si trascina (aggiornata via onGloballyPositioned)
    var originInWindow by remember { mutableStateOf(Offset.Unspecified) }

    // catturiamo l'origine appena prima dell'inizio del drag per mantenere riferimento fisso
    var originOnDragStart by remember { mutableStateOf<Offset?>(null) }
    // posizione del puntatore relativa al composable al momento dell'onDragStart
    var initialPointerLocal by remember { mutableStateOf<Offset?>(null) }

    // ultima cella calcolata durante il drag
    var lastCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                originInWindow = Offset(pos.x, pos.y)
            }
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = { pointerOffset ->
                        // salva snapshot dell'origine e della posizione del puntatore RELATIVE alla composable
                        originOnDragStart = originInWindow
                        initialPointerLocal = pointerOffset
                        dragOffset = Offset.Zero
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // aggiorna posizione visiva
                        dragOffset += dragAmount

                        // se non abbiamo metriche della board o originOnDragStart, non provare ad aggiornare preview
                        val originStart = originOnDragStart
                        val initialLocal = initialPointerLocal
                        if (originStart == null || initialLocal == null || boardOffset == Offset.Unspecified || boardCellPx <= 0f) {
                            return@detectDragGestures
                        }

                        // calcola posizione assoluta del puntatore (window coordinates)
                        // fingerAbs = originOnDragStart + initialPointerLocal + dragOffset
                        val fingerAbsX = originInWindow.x + dragOffset.x + change.position.x
                        val fingerAbsY = originInWindow.y + dragOffset.y + change.position.y

                        // posizione relativa alla board
                        val relX = fingerAbsX - boardOffset.x
                        val relY = fingerAbsY - boardOffset.y

                        val boardSizePx = boardCellPx * BOARD_GRID

                        // se il puntatore è dentro la board aggiorna preview altrimenti clear preview
                        val insideBoard = relX >= 0f && relY >= 0f && relX < boardSizePx && relY < boardSizePx

                        if (insideBoard) {
                            val col = (relX / boardCellPx).toInt().coerceIn(0, BOARD_GRID - 1)
                            val row = (relY / boardCellPx).toInt().coerceIn(0, BOARD_GRID - 1)
                            lastCell = row to col
                            onDragCell(row, col)
                        } else {
                            lastCell = null
                            onDragExit() // cancella la preview mentre sei fuori
                        }
                    },
                    onDragEnd = {
                        // droppa solo se siamo effettivamente sopra una cella valida
                        lastCell?.let { (r, c) ->
                            onDrop(r, c)
                        } ?: run {
                            // fuori dalla board -> snap back + cancella preview
                            onDragExit()
                        }
                        dragOffset = Offset.Zero
                        lastCell = null
                        originOnDragStart = null
                        initialPointerLocal = null
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        lastCell = null
                        originOnDragStart = null
                        initialPointerLocal = null
                        onDragExit()
                    }
                )
            }
            .size(CELL_SIZE * size, CELL_SIZE)
    ) {
        // semplice disegno della nave nella banchina (coerente con ShipDrawing)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SHIP_COLOR, RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (size > 1) "🛳️" else "🚤", fontSize = if (size > 1) 26.sp else 20.sp)
        }
    }
}

// ------------------ BattlePhase ------------------
@Composable
fun BattlePhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text("Enemy Grid", fontSize = 18.sp, color = Color.White)
        GameBoard(
            player = gameState.player2,
            onCellClick = { r, c -> gameViewModel.onCellClick(r, c) },
            isOpponentBoard = true,
            phase = GamePhase.BATTLE,
            gameViewModel = gameViewModel,
            onBoardMetricsChanged = null
        )

        // Mostra "Your Turn" quando è il tuo turno; altrimenti (se è il computer) mostra "Computer is thinking..."
        val isComputerTurn = gameState.currentPlayer.isComputer && gameState.phase == GamePhase.BATTLE

        if (!isComputerTurn) {
            Text("Your Turn", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        } else {
            // puoi animare/mostrare la scritta solo durante il turno del computer
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text("Computer is thinking...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Text("Your Grid", fontSize = 18.sp, color = Color.White)
        GameBoard(
            player = gameState.player1,
            onCellClick = { _, _ -> },
            isOpponentBoard = false,
            phase = GamePhase.BATTLE,
            gameViewModel = gameViewModel,
            onBoardMetricsChanged = null
        )
    }
}

// ------------------ FinishedPhase ------------------
@Composable
fun FinishedPhase(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.gameState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val winnerText = if (gameState.winner == gameState.player1) "You Win!" else "Computer Wins!"
        Text(winnerText, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Yellow)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { gameViewModel.resetGame() }) {
            Text("Play Again")
        }
    }
}

// ------------------ GameBoard ------------------
@Composable
fun GameBoard(
    player: Player,
    onCellClick: (Int, Int) -> Unit,
    isOpponentBoard: Boolean,
    phase: GamePhase,
    gameViewModel: GameViewModel?,
    onBoardMetricsChanged: ((Offset, Float, Int) -> Unit)?
) {
    val density = LocalDensity.current
    val cellPx = with(density) { CELL_SIZE.toPx() }
    val gridSize = player.grid.size

    Box(
        modifier = Modifier
            .border(4.dp, Color.White, RoundedCornerShape(12.dp))
            .background(BOARD_BG, RoundedCornerShape(12.dp))
            .padding(8.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                onBoardMetricsChanged?.invoke(Offset(pos.x, pos.y), cellPx, gridSize)
            }
    ) {
        // layer celle
        Column {
            repeat(gridSize) { r ->
                Row {
                    repeat(gridSize) { c ->
                        val state = gameViewModel?.gameState?.collectAsState()?.value
                        val preview = state?.placementPreview
                        val isPreview = preview?.coordinates?.contains(r to c) == true
                        val isPreviewValid = preview?.isValid == true
                        GridCell(
                            cell = player.grid[r][c],
                            onCellClick = onCellClick,
                            isPreview = isPreview,
                            isPreviewValid = isPreviewValid,
                            phase = phase,
                            isOpponentBoard = isOpponentBoard
                        )
                    }
                }
            }
        }

        // layer navi visibili
        if (!isOpponentBoard) {
            player.ships.forEach { ship ->
                // la ShipDrawing usa coordinate della nave
                ShipDrawing(ship, CELL_SIZE)
            }
        }

        // marker hit/miss sovrapposti
        Column {
            repeat(gridSize) { r ->
                Row {
                    repeat(gridSize) { c ->
                        val cell = player.grid[r][c]
                        HitMissMarker(cell, CELL_SIZE)
                    }
                }
            }
        }

        // sunk message overlay (solo per board nemica se presente)
        if (player.isComputer && gameViewModel != null) {
            val state = gameViewModel.gameState.collectAsState().value
            AnimatedVisibility(
                visible = state.sunkMessage != null,
                enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(state.sunkMessage ?: "", color = Color.Yellow, fontSize = 18.sp)
                }
            }
        }
    }
}

// ------------------ GridCell / Preview / Markers / ShipDrawing ------------------
@Composable
fun GridCell(
    cell: Cell,
    onCellClick: (Int, Int) -> Unit,
    isPreview: Boolean,
    isPreviewValid: Boolean,
    phase: GamePhase,
    isOpponentBoard: Boolean
) {
    val bg = when {
        isPreview && isPreviewValid -> PREVIEW_OK
        isPreview && !isPreviewValid -> PREVIEW_BAD
        cell.status == CellStatus.SHIP && !isOpponentBoard -> Color(0xFF8EA2C6)
        else -> Color(0xFF5FC0FF)
    }

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .border(1.dp, Color.White)
            .background(bg)
            .pointerInput(cell.row, cell.col) {
                detectTapGestures { onCellClick(cell.row, cell.col) }
            }
    ) {
        // cell empty: hit/miss markers sono disegnati da HitMissMarker sovrapposto
    }
}

@Composable
fun PlacementPreviewDrawing(preview: PlacementPreview, cellSize: Dp) {
    val color = if (preview.isValid) PREVIEW_OK else PREVIEW_BAD
    preview.coordinates.forEach { (r, c) ->
        Box(
            modifier = Modifier
                .offset(x = cellSize * c, y = cellSize * r)
                .size(cellSize)
                .background(color)
        )
    }
}

@Composable
fun HitMissMarker(cell: Cell, cellSize: Dp) {
    Box(modifier = Modifier.size(cellSize), contentAlignment = Alignment.Center) {
        when (cell.status) {
            CellStatus.HIT -> Text("💥", fontSize = 28.sp)
            CellStatus.MISS -> Text("❌", fontSize = 20.sp)
            else -> { /* nothing */ }
        }
    }
}

@Composable
fun ShipDrawing(ship: Ship, cellSize: Dp) {
    // se coords vuote -> non disegna (ma nella tua logica le ships hanno sempre coords quando piazzate)
    if (ship.coordinates.isEmpty()) return
    val (minRow, minCol) = ship.coordinates.minWithOrNull(compareBy({ it.first }, { it.second })) ?: return
    val isHorizontal = ship.orientation == ShipOrientation.HORIZONTAL
    val width = if (isHorizontal) cellSize * ship.size else cellSize
    val height = if (isHorizontal) cellSize else cellSize * ship.size

    Box(
        modifier = Modifier
            .offset(x = cellSize * minCol, y = cellSize * minRow)
            .size(width, height)
            .background(SHIP_COLOR, RoundedCornerShape(8.dp))
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(if (ship.size > 1) "🛳️" else "🚤", fontSize = if (ship.size > 1) 34.sp else 24.sp)
    }
}
