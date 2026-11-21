package io.github.woroxiv.tvremote

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import io.github.woroxiv.tvremote.discovery.FoundTv
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.model.TvDevice
import io.github.woroxiv.tvremote.protocol.androidtv.AndroidTvController
import io.github.woroxiv.tvremote.ui.screens.DevicesScreen
import io.github.woroxiv.tvremote.ui.screens.RemoteScreen
import io.github.woroxiv.tvremote.ui.theme.TvRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG files in build/screenshots so the UI can be
 * reviewed without a device. Run with: ./gradlew :app:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent { TvRemoteTheme(content) }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val salon = TvDevice("ANDROID_TV:192.168.1.20", "TV du salon", Protocol.ANDROID_TV, "192.168.1.20")

    @Test
    fun devices() = snap("devices") {
        DevicesScreen(
            devices = listOf(
                salon,
                TvDevice("SAMSUNG:192.168.1.31", "Samsung chambre", Protocol.SAMSUNG, "192.168.1.31"),
            ),
            found = listOf(
                FoundTv("192.168.1.44", "[LG] webOS TV OLED55", Protocol.LG),
                FoundTv("192.168.1.52", "Roku Express", Protocol.ROKU),
            ),
            scanning = true,
            hasIrEmitter = true,
            onScan = {}, onOpen = {}, onAddFound = {}, onAddManual = { _, _, _ -> },
            onAddIr = { _: IrBrand -> }, onRename = { _, _ -> }, onRemove = {},
        )
    }

    @Test
    fun devicesEmpty() = snap("devices_empty") {
        DevicesScreen(
            devices = emptyList(), found = emptyList(), scanning = false, hasIrEmitter = false,
            onScan = {}, onOpen = {}, onAddFound = {}, onAddManual = { _, _, _ -> },
            onAddIr = { _: IrBrand -> }, onRename = { _, _ -> }, onRemove = {},
        )
    }

    @Test
    fun remoteConnected() = snap("remote") {
        RemoteScreen(
            device = salon, state = ConnState.Connected,
            supportedKeys = AndroidTvController.KEYCODES.keys, supportsText = true,
            onBack = {}, onKey = { _: RemoteKey -> }, onText = {}, onRetry = {}, onPairingCode = { true },
        )
    }

    @Test
    fun remoteError() = snap("remote_error") {
        RemoteScreen(
            device = salon, state = ConnState.Error("TV injoignable. Vérifiez qu'elle est allumée et sur le même Wi-Fi."),
            supportedKeys = AndroidTvController.KEYCODES.keys, supportsText = true,
            onBack = {}, onKey = { _: RemoteKey -> }, onText = {}, onRetry = {}, onPairingCode = { true },
        )
    }
}
