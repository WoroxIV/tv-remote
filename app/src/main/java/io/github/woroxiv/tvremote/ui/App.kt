package io.github.woroxiv.tvremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.ui.screens.DevicesScreen
import io.github.woroxiv.tvremote.ui.screens.RemoteScreen

@Composable
fun App(vm: AppViewModel) {
    val current by vm.current.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        // Open the last-used TV directly when there is exactly one, otherwise scan.
        if (vm.current.value == null) vm.devices.value.singleOrNull()?.let { vm.open(it) } ?: vm.scan()
    }

    BackHandler(enabled = current != null) { vm.closeRemote() }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        contentKey = { it?.id },
        label = "screen",
    ) { device ->
        if (device == null) {
            DevicesScreen(
                devices = devices,
                found = found,
                scanning = scanning,
                hasIrEmitter = vm.hasIrEmitter,
                onScan = vm::scan,
                onOpen = vm::open,
                onAddFound = vm::addFound,
                onAddManual = vm::addManual,
                onAddIr = vm::addIr,
                onRename = vm::rename,
                onRemove = vm::remove,
            )
        } else {
            val c = controller
            val state = c?.state?.collectAsState()?.value ?: ConnState.Idle
            // Pick up a MAC address learned while connected.
            val fresh = devices.firstOrNull { it.id == device.id } ?: device
            RemoteScreen(
                device = fresh,
                state = state,
                supportedKeys = c?.supportedKeys ?: emptySet(),
                supportsText = c?.supportsText ?: false,
                onBack = vm::closeRemote,
                onKey = vm::press,
                onText = vm::sendText,
                onRetry = vm::reconnect,
                onPairingCode = vm::submitPairingCode,
            )
        }
    }
}
