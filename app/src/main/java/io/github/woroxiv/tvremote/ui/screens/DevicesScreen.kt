package io.github.woroxiv.tvremote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.woroxiv.tvremote.discovery.FoundTv
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.model.TvDevice
import io.github.woroxiv.tvremote.ui.theme.Palette

fun protocolColor(p: Protocol): Color = when (p) {
    Protocol.ANDROID_TV -> Color(0xFF34D399)
    Protocol.SAMSUNG -> Color(0xFF60A5FA)
    Protocol.LG -> Color(0xFFF472B6)
    Protocol.ROKU -> Color(0xFFA78BFA)
    Protocol.IR -> Color(0xFFFBBF24)
}

fun protocolIcon(p: Protocol): ImageVector = when (p) {
    Protocol.ANDROID_TV -> Icons.Rounded.Cast
    Protocol.IR -> Icons.Rounded.SettingsRemote
    else -> Icons.Rounded.Tv
}

@Composable
fun DevicesScreen(
    devices: List<TvDevice>,
    found: List<FoundTv>,
    scanning: Boolean,
    hasIrEmitter: Boolean,
    onScan: () -> Unit,
    onOpen: (TvDevice) -> Unit,
    onAddFound: (FoundTv) -> Unit,
    onAddManual: (String, String, Protocol) -> Unit,
    onAddIr: (IrBrand) -> Unit,
    onRename: (TvDevice, String) -> Unit,
    onRemove: (TvDevice) -> Unit,
) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showIr by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManual = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Ajouter une TV") },
                containerColor = Palette.Accent,
                contentColor = Color.White,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Header(onAbout = { showAbout = true }) }

            if (hasIrEmitter) {
                item { SectionTitle("Infrarouge") }
                item { IrCard(hasIrEmitter, onClick = { showIr = true }) }
            }

            if (devices.isNotEmpty()) {
                item { SectionTitle("Mes téléviseurs") }
                items(devices, key = { it.id }) { d ->
                    SavedDeviceCard(d, onOpen = { onOpen(d) }, onRename = { onRename(d, it) }, onRemove = { onRemove(d) })
                }
            }

            item {
                SectionTitle(
                    "Sur votre Wi-Fi",
                    trailing = {
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent2)
                        } else {
                            IconButton(onClick = onScan) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Relancer la recherche", tint = Palette.TextDim)
                            }
                        }
                    },
                )
            }
            if (found.isEmpty()) {
                item { EmptyScan(scanning) }
            } else {
                items(found, key = { "found:" + it.host + it.protocol }) { tv ->
                    DeviceRow(
                        title = tv.name,
                        subtitle = "${tv.protocol.label} · ${tv.host}",
                        protocol = tv.protocol,
                        onClick = { onAddFound(tv) },
                        trailing = { Icon(Icons.Rounded.Add, contentDescription = "Ajouter", tint = Palette.Accent2) },
                    )
                }
            }

            if (!hasIrEmitter) {
                item { SectionTitle("Sans Wi-Fi") }
                item { IrCard(hasIrEmitter, onClick = { showIr = true }) }
            }
        }
    }

    if (showManual) ManualDialog(
        hasIrEmitter = hasIrEmitter,
        onDismiss = { showManual = false },
        onAdd = { n, h, p -> showManual = false; onAddManual(n, h, p) },
        onAddIr = { showManual = false; onAddIr(it) },
    )
    if (showIr) {
        if (hasIrEmitter) IrBrandDialog(onDismiss = { showIr = false }, onPick = { showIr = false; onAddIr(it) })
        else NoIrDialog(onDismiss = { showIr = false })
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun IrCard(hasIrEmitter: Boolean, onClick: () -> Unit) {
    DeviceRow(
        title = "Télécommande infrarouge",
        subtitle = if (hasIrEmitter) "Toutes les TV, même sans Wi-Fi"
        else "Non disponible sur ce téléphone",
        protocol = Protocol.IR,
        onClick = onClick,
        trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Palette.TextDim) },
    )
}

@Composable
private fun NoIrDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pas d'émetteur infrarouge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Android indique que ce téléphone n'a pas d'émetteur infrarouge. " +
                        "C'est une petite fenêtre noire sur la tranche du haut, présente surtout sur les " +
                        "Xiaomi, Redmi, Poco, certains Honor/Huawei et quelques anciens Samsung/LG."
                )
                Text(
                    "Bonne nouvelle : la plupart des TV récentes se pilotent par le Wi-Fi. " +
                        "Allumez la TV, connectez-la au même Wi-Fi que le téléphone, et elle apparaîtra dans " +
                        "« Sur votre Wi-Fi » — ou utilisez « Ajouter une TV » avec son adresse IP.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Compris") } },
    )
}

@Composable
private fun Header(onAbout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.AccentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SettingsRemote, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Zap Remote", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Text("Gratuite · sans pub · sans compte", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        IconButton(onClick = onAbout) {
            Icon(Icons.Rounded.Info, contentDescription = "À propos", tint = Palette.TextDim)
        }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun EmptyScan(scanning: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.WifiFind, contentDescription = null, tint = Palette.Accent2, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            if (scanning) "Recherche des téléviseurs…" else "Aucune TV trouvée",
            style = MaterialTheme.typography.titleMedium, color = Palette.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Le téléphone et la TV doivent être allumés et sur le même Wi-Fi. " +
                "Vous pouvez aussi ajouter la TV avec son adresse IP.",
            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
        )
    }
}

@Composable
fun ProtocolBadge(protocol: Protocol, size: Int = 44) {
    val color = protocolColor(protocol)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(protocolIcon(protocol), contentDescription = null, tint = color, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    protocol: Protocol,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtocolBadge(protocol)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SavedDeviceCard(device: TvDevice, onOpen: () -> Unit, onRename: (String) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    DeviceRow(
        title = device.name,
        subtitle = device.subtitle,
        protocol = device.protocol,
        onClick = onOpen,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = Palette.TextDim)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        },
    )
    if (renaming) {
        var name by remember { mutableStateOf(device.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renommer") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom") }) },
            confirmButton = { TextButton(onClick = { renaming = false; onRename(name) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${device.name} » ?") },
            text = { Text("Il faudra l'associer à nouveau pour l'utiliser.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onRemove() }) { Text("Supprimer", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private val manualProtocols = listOf(Protocol.ANDROID_TV, Protocol.SAMSUNG, Protocol.LG, Protocol.ROKU, Protocol.IR)

@Composable
private fun ManualDialog(
    hasIrEmitter: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, Protocol) -> Unit,
    onAddIr: (IrBrand) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf(Protocol.ANDROID_TV) }
    var brand by rememberSaveable { mutableStateOf<IrBrand?>(null) }
    val validHost = host.trim().matches(Regex("^[A-Za-z0-9.\\-:]+$"))
    val ir = protocol == Protocol.IR
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type de TV", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    manualProtocols.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                FilterChip(
                                    selected = protocol == p,
                                    onClick = { protocol = p },
                                    label = { Text(p.label.substringBefore(" /").substringBefore(" Smart")) },
                                )
                            }
                        }
                    }
                }
                if (ir) {
                    if (!hasIrEmitter) {
                        Text(
                            "Aucun émetteur infrarouge n'est détecté sur ce téléphone : cette option ne " +
                                "fonctionnera pas. Utilisez plutôt le Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Warning,
                        )
                    }
                    Text("Marque", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IrBrand.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { b ->
                                    FilterChip(selected = brand == b, onClick = { brand = b }, label = { Text(b.label) })
                                }
                            }
                        }
                    }
                    Text(
                        "Pointez le haut du téléphone vers la TV.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                } else {
                    OutlinedTextField(
                        host, { host = it }, singleLine = true,
                        label = { Text("Adresse IP (ex. 192.168.1.20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom (facultatif)") })
                    Text(
                        "L'adresse IP se trouve dans les réglages réseau de la TV.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                }
            }
        },
        confirmButton = {
            if (ir) {
                TextButton(enabled = brand != null && hasIrEmitter, onClick = { brand?.let(onAddIr) }) { Text("Ouvrir") }
            } else {
                TextButton(enabled = validHost, onClick = { onAdd(name, host, protocol) }) { Text("Connecter") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun IrBrandDialog(onDismiss: () -> Unit, onPick: (IrBrand) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marque de la TV") },
        text = {
            Column {
                Text(
                    "Pointez le haut du téléphone vers la TV.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                IrBrand.entries.forEach { brand ->
                    Text(
                        brand.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(brand) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zap Remote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("100 % gratuit, sans publicité, sans compte et sans traceur. Tout reste sur votre réseau local.")
                Text("Compatible :", style = MaterialTheme.typography.labelLarge)
                Text(
                    "• Google TV / Android TV (Sony, Philips, TCL, Hisense, Xiaomi, Chromecast…)\n" +
                        "• Samsung Smart TV (2016 et plus)\n" +
                        "• LG webOS\n" +
                        "• Roku TV\n" +
                        "• Infrarouge si le téléphone a un émetteur IR",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Logiciel libre — github.com/WoroxIV/tv-remote", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
