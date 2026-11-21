package io.github.woroxiv.tvremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.ui.App
import io.github.woroxiv.tvremote.ui.AppViewModel
import io.github.woroxiv.tvremote.ui.theme.TvRemoteTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TvRemoteTheme { App(vm) }
        }
    }

    /** The phone's own volume buttons control the TV while a remote is open. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val connected = vm.controller.value?.state?.value is ConnState.Connected
        if (connected) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { vm.press(RemoteKey.VOL_UP); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { vm.press(RemoteKey.VOL_DOWN); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
