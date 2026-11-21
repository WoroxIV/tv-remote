package io.github.woroxiv.tvremote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.model.TvDevice
import io.github.woroxiv.tvremote.ui.components.DPad
import io.github.woroxiv.tvremote.ui.components.RemoteButton
import io.github.woroxiv.tvremote.ui.components.Rocker
import io.github.woroxiv.tvremote.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    device: TvDevice,
    state: ConnState,
    supportedKeys: Set<RemoteKey>,
    supportsText: Boolean,
    onBack: () -> Unit,
    onKey: (RemoteKey) -> Unit,
    onText: (String) -> Unit,
    onRetry: () -> Unit,
    onPairingCode: suspend (String) -> Boolean,
) {
    var showDigits by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    val ready = state is ConnState.Connected
    // POWER stays usable when disconnected so Samsung/LG can be woken up.
    fun enabled(k: RemoteKey) = k in supportedKeys && (ready || (k == RemoteKey.POWER && device.mac != null))

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        TopBar(device, state, supportsText && ready, onBack, onKeyboard = { showKeyboard = true })

        AnimatedVisibility(
            visible = !ready,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            StatusBanner(device, state, onRetry)
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            val padSize = minOf(maxWidth, 300.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RemoteButton(
                        "Marche / Arrêt", icon = Icons.Rounded.PowerSettingsNew, size = 60.dp,
                        enabled = enabled(RemoteKey.POWER),
                        background = Brush.verticalGradient(listOf(Color(0xFF3A1820), Color(0xFF26121A))),
                        contentColor = Palette.Danger,
                    ) { onKey(RemoteKey.POWER) }
                    Spacer(Modifier.weight(1f))
                    RemoteButton("Source", icon = Icons.Rounded.Input, size = 52.dp, enabled = enabled(RemoteKey.INPUT)) { onKey(RemoteKey.INPUT) }
                    Spacer(Modifier.width(12.dp))
                    RemoteButton("Réglages", icon = Icons.Rounded.Settings, size = 52.dp, enabled = enabled(RemoteKey.SETTINGS)) { onKey(RemoteKey.SETTINGS) }
                    Spacer(Modifier.width(12.dp))
                    RemoteButton("Silence", icon = Icons.AutoMirrored.Rounded.VolumeOff, size = 52.dp, enabled = enabled(RemoteKey.MUTE)) { onKey(RemoteKey.MUTE) }
                }

                Spacer(Modifier.height(28.dp))
                DPad(size = padSize, enabled = ready, onKey = onKey)
                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RemoteButton("Retour", icon = Icons.AutoMirrored.Rounded.Undo, enabled = enabled(RemoteKey.BACK)) { onKey(RemoteKey.BACK) }
                    RemoteButton("Accueil", icon = Icons.Rounded.Home, enabled = enabled(RemoteKey.HOME)) { onKey(RemoteKey.HOME) }
                    RemoteButton("Menu", icon = Icons.Rounded.Menu, enabled = enabled(RemoteKey.MENU)) { onKey(RemoteKey.MENU) }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Rocker(
                        "VOL", Icons.Rounded.Add, Icons.Rounded.Remove, "Volume plus", "Volume moins",
                        enabled = enabled(RemoteKey.VOL_UP),
                        onUp = { onKey(RemoteKey.VOL_UP) }, onDown = { onKey(RemoteKey.VOL_DOWN) },
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RemoteButton("Pavé numérique", icon = Icons.Rounded.Dialpad, size = 56.dp, enabled = enabled(RemoteKey.DIGIT_1)) { showDigits = true }
                        RemoteButton("Infos", icon = Icons.Rounded.Info, size = 56.dp, enabled = enabled(RemoteKey.INFO)) { onKey(RemoteKey.INFO) }
                    }
                    Rocker(
                        "CH", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown, "Chaîne suivante", "Chaîne précédente",
                        enabled = enabled(RemoteKey.CH_UP),
                        onUp = { onKey(RemoteKey.CH_UP) }, onDown = { onKey(RemoteKey.CH_DOWN) },
                    )
                }

                Spacer(Modifier.height(24.dp))
                MediaBar(::enabled, onKey)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDigits) {
        ModalBottomSheet(onDismissRequest = { showDigits = false }, containerColor = Palette.Surface) {
            DigitPad(onKey)
        }
    }
    if (showKeyboard) {
        ModalBottomSheet(onDismissRequest = { showKeyboard = false }, containerColor = Palette.Surface) {
            KeyboardSheet(onText)
        }
    }
    if (state is ConnState.NeedsPairingCode) {
        PairingDialog(onSubmit = onPairingCode, onCancel = onBack)
    }
}

@Composable
private fun TopBar(device: TvDevice, state: ConnState, keyboard: Boolean, onBack: () -> Unit, onKeyboard: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour à la liste", tint = Palette.Text)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (color, label) = when (state) {
                    ConnState.Connected -> Palette.Success to (if (device.protocol == Protocol.IR) "Infrarouge prêt" else "Connecté")
                    is ConnState.Error -> Palette.Danger to "Déconnecté"
                    ConnState.NeedsPairingCode, ConnState.WaitingForTvApproval -> Palette.Warning to "Association…"
                    else -> Palette.Warning to "Connexion…"
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
        if (keyboard) {
            IconButton(onClick = onKeyboard) {
                Icon(Icons.Rounded.Keyboard, contentDescription = "Clavier", tint = Palette.Text)
            }
        }
    }
}

@Composable
private fun StatusBanner(device: TvDevice, state: ConnState, onRetry: () -> Unit) {
    val (title, body) = when (state) {
        is ConnState.Error -> "Connexion impossible" to state.message
        ConnState.WaitingForTvApproval -> "Acceptez sur la TV" to
            "Une demande d'autorisation s'affiche sur l'écran de la TV. Sélectionnez « Autoriser » avec la télécommande d'origine."
        ConnState.NeedsPairingCode -> "Association" to "Saisissez le code affiché sur la TV."
        else -> "Connexion à ${device.name}…" to null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state !is ConnState.Error) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Palette.Accent2)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
        }
        if (state is ConnState.Error) {
            Spacer(Modifier.width(10.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
private fun MediaBar(enabled: (RemoteKey) -> Boolean, onKey: (RemoteKey) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(32.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteButton("Retour rapide", icon = Icons.Rounded.FastRewind, size = 48.dp, enabled = enabled(RemoteKey.REWIND), repeat = true) { onKey(RemoteKey.REWIND) }
        RemoteButton(
            "Lecture / Pause", icon = Icons.Rounded.PlayArrow, size = 60.dp,
            enabled = enabled(RemoteKey.PLAY_PAUSE), background = Palette.AccentGradient, contentColor = Color.White,
        ) { onKey(RemoteKey.PLAY_PAUSE) }
        RemoteButton("Avance rapide", icon = Icons.Rounded.FastForward, size = 48.dp, enabled = enabled(RemoteKey.FAST_FORWARD), repeat = true) { onKey(RemoteKey.FAST_FORWARD) }
        if (RemoteKey.STOP.let(enabled)) {
            RemoteButton("Stop", icon = Icons.Rounded.Stop, size = 48.dp) { onKey(RemoteKey.STOP) }
        }
    }
}

@Composable
private fun DigitPad(onKey: (RemoteKey) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Chaîne", style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, null)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { n ->
                    if (n == null) Spacer(Modifier.size(72.dp))
                    else RemoteButton("$n", text = "$n", size = 72.dp) { onKey(RemoteKey.digit(n)) }
                }
            }
        }
    }
}

@Composable
private fun KeyboardSheet(onText: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Écrire sur la TV", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Text(
            "Sélectionnez d'abord un champ de recherche sur la TV.",
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Rechercher un film, une appli…") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotEmpty()) { onText(text); text = "" } }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onText("\b") }) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer")
            }
            OutlinedButton(onClick = { onText("\n") }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = "Entrée")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onText(text); text = "" },
                enabled = text.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Envoyer")
            }
        }
    }
}

@Composable
private fun PairingDialog(onSubmit: suspend (String) -> Boolean, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (code.length != 6 || busy) return
        busy = true
        scope.launch {
            error = !onSubmit(code)
            busy = false
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Code d'association") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saisissez le code à 6 caractères affiché sur la TV.", color = Palette.TextDim)
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                        error = false
                    },
                    singleLine = true,
                    isError = error,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    supportingText = { if (error) Text("Code incorrect, vérifiez l'écran de la TV.") },
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = code.length == 6 && !busy, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Associer")
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancel) { Text("Annuler") } },
    )
}
