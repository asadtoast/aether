package com.asadtoast.aether.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.DuplicateAction

@Composable
fun DuplicateProcessingScreen(
    viewModel: DuplicateViewModel,
    onNavigateBack: () -> Unit
) {
    val duplicatePairs by viewModel.duplicatePairs.collectAsState()
    val totalPairsCount by viewModel.totalPairsCount.collectAsState()
    val totalFilesCount by viewModel.totalFilesCount.collectAsState()
    val currentPair = duplicatePairs.firstOrNull()

    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val apiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")
    val serverIp by settingsManager.serverIpFlow.collectAsState(initial = "")
    val port by settingsManager.serverPortFlow.collectAsState(initial = "")

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // restore on exit
            val windowToRestore = (context as? Activity)?.window
            if (windowToRestore != null) {
                WindowCompat.getInsetsController(windowToRestore, windowToRestore.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // load queue if empty
    LaunchedEffect(Unit) {
        if (duplicatePairs.isEmpty()) {
            viewModel.fetchDuplicatePotentials()
        }
    }

    if (currentPair == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No duplicates available for processing", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("Go back")
                }
            }
        }
        return
    }

    // status to decide which picture gets shown when holding
    var showImageB by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // image viewer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            showImageB = true
                            tryAwaitRelease()
                            showImageB = false
                        }
                    )
                },
            contentAlignment = Alignment.TopStart
        ) {
            // image layers
            DuplicateImageLayer(currentPair.hash_a, apiKey, serverIp, port, isVisible = !showImageB)
            DuplicateImageLayer(currentPair.hash_b, apiKey, serverIp, port, isVisible = showImageB)

            // overlay
            val activeMeta = if (showImageB) currentPair.meta_b else currentPair.meta_a
            FileMetadataOverlay(
                size = activeMeta.size ?: 0L,
                width = activeMeta.width ?: 0,
                height = activeMeta.height ?: 0,
                tagCount = activeMeta.tagCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 25.dp, end = 25.dp)
            )

            // A/B visual indicator
            Text(
                text = if (showImageB) "B" else "A",
                color = Color.Red.copy(alpha = 0.8f),
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // HUD for remaining counts
            Text(
                text = "Pairs: $totalPairsCount | Files: $totalFilesCount",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 25.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // bottom control panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // row 1: main
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.A_IS_BETTER) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("A is better", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.B_IS_BETTER) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("B is better", textAlign = TextAlign.Center)
                }
            }

            // row 2: alternates
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.ALTERNATES) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Alternates", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.FALSE_POSITIVE) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("False Pos.", textAlign = TextAlign.Center)
                }
            }

            // row 3: destructive
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.DELETE_BOTH) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete Both", textAlign = TextAlign.Center, color = Color.White)
                }
                OutlinedButton(
                    onClick = { viewModel.undoLastAction() },
                    modifier = Modifier.weight(0.5f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Undo", textAlign = TextAlign.Center)
                }
                TextButton(
                    onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.SKIP) },
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Skip", textAlign = TextAlign.Center, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun DuplicateImageLayer(hash: String, apiKey: String, serverIp: String, port: String, isVisible: Boolean) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("http://$serverIp:$port/get_files/file?hash=$hash&Hydrus-Client-API-Access-Key=$apiKey")
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = "Duplicate Image",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().alpha(if (isVisible) 1f else 0f)
    )
}
