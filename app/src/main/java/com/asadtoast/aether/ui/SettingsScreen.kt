package com.asadtoast.aether.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asadtoast.aether.data.SettingsManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onTestConnection: suspend (ip: String, port: String, apiKey: String) -> Boolean,
    onSettingsSaved: () -> Unit,
    onCancel: () -> Unit
) {
    // read data to autofill fields
    val currentIp by settingsManager.serverIpFlow.collectAsState(initial = "127.0.0.1")
    val currentApiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")

    // dark mode value
    val isDarkMode by settingsManager.darkModeFlow.collectAsState(initial = false)

    // privacy keyboard value
    val isPrivacyKeyboard by settingsManager.privacyKeyboardFlow.collectAsState(initial = true)

    val hidePdf by settingsManager.hidePdfFlow.collectAsState(initial = false)
    val hidePsd by settingsManager.hidePsdFlow.collectAsState(initial = false)
    val hideZip by settingsManager.hideZipFlow.collectAsState(initial = false)

    // max hamming distance
    val currentMaxHamming by settingsManager.maxHammingDistanceFlow.collectAsState(initial = 4)
    var maxHammingState by remember(currentMaxHamming) { mutableFloatStateOf(currentMaxHamming.toFloat()) }

    var ipInput by remember(currentIp) { mutableStateOf(currentIp.ifEmpty { "127.0.0.1" }) }
    var portInput by remember { mutableStateOf("45869") }
    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }

    var connectionStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // slider, amount of thumbnails
    val currentGridColumns by settingsManager.gridColumnsFlow.collectAsState(initial = 3)
    var gridColumnsState by remember(currentGridColumns) { mutableFloatStateOf(currentGridColumns.toFloat()) }

    val context = LocalContext.current
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    // record back button
    BackHandler {
        onCancel()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Server config", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        // IP and port
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("IP Address") },
                modifier = Modifier.weight(0.7f),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Unspecified,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )

            )
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it },
                label = { Text("Port") },
                modifier = Modifier.weight(0.3f),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Unspecified,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("API Access Key") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Unspecified,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // test connection button
        Button(
            onClick = {
                // antispam
                if (isTesting) return@Button

                isTesting = true
                connectionStatus = "Testing"

                coroutineScope.launch {
                    // animated dots routine
                    val animJob = launch {
                        var dots = 0
                        while (isActive) {
                            val dotString = ".".repeat(dots % 4)
                            connectionStatus = "Testing$dotString"
                            dots++
                            kotlinx.coroutines.delay(300.milliseconds)
                        }
                    }

                    try {
                        val success = onTestConnection(ipInput, portInput, apiKeyInput)
                        animJob.cancel()
                        connectionStatus = if (success) "Success!" else "Error: check settings"

                    } catch (_: Exception) {
                        // if wrong, error
                        animJob.cancel()
                        connectionStatus = "Error: check settings"

                    } finally {
                        isTesting = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Test connection")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // dynamic test colors
        if (connectionStatus.isNotEmpty()) {
            val textColor = when {
                connectionStatus.startsWith("Testing") -> Color.White
                connectionStatus.contains("Error") -> Color.Red
                else -> Color.Green
            }
            Text(
                text = connectionStatus,
                color = textColor
            )
        }


        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Darkmode", style = MaterialTheme.typography.bodyLarge)

            Switch(
                checked = isDarkMode,
                onCheckedChange = { isEnabled ->
                    coroutineScope.launch {
                        settingsManager.saveDarkMode(isEnabled)
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Keyboard Privacy", style = MaterialTheme.typography.bodyLarge)

            Switch(
                checked = isPrivacyKeyboard,
                onCheckedChange = { isEnabled ->
                    coroutineScope.launch {
                        settingsManager.savePrivacyKeyboard(isEnabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // gallery settings
        Text(
            text = "Gallery settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        // thumbnail column count slider
        Text(
            text = "Gallery columns: ${gridColumnsState.toInt()}",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = gridColumnsState,
            onValueChange = { gridColumnsState = it },
            onValueChangeFinished = {
                coroutineScope.launch {
                    settingsManager.saveGridColumns(gridColumnsState.toInt())
                }
            },
            valueRange = 1f..6f,
            steps = 4
        )

        Spacer(modifier = Modifier.height(24.dp))

        // duplicate processing settings
        Text(
            text = "Duplicate processing settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // search distance slider
        Text(
            text = "Search distance slider: ${maxHammingState.toInt()}",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = maxHammingState,
            onValueChange = { maxHammingState = it },
            onValueChangeFinished = {
                coroutineScope.launch {
                    settingsManager.saveMaxHammingDistance(maxHammingState.toInt())
                }
            },
            valueRange = 0f..8f,
            steps = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        // content filters
        Text(
            text = "Content filters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // PDF switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hide PDF", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = hidePdf,
                onCheckedChange = { isEnabled ->
                    coroutineScope.launch {
                        settingsManager.saveHidePdf(isEnabled)
                    }
                }
            )
        }

        // PSD switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hide PSD", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = hidePsd,
                onCheckedChange = { isEnabled ->
                    coroutineScope.launch {
                        settingsManager.saveHidePsd(isEnabled)
                    }
                }
            )
        }

        // Zip switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hide ZIP", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = hideZip,
                onCheckedChange = { isEnabled ->
                    coroutineScope.launch {
                        settingsManager.saveHideZip(isEnabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Version $appVersion",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(vertical = 24.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
        // save and exit button
        Button(
            onClick = {
                coroutineScope.launch {
                    settingsManager.saveSettings(
                        apiKey = apiKeyInput,
                        serverIp = ipInput,
                        port = portInput
                    )
                    onSettingsSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and exit")
        }
    }
}