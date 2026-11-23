package io.github.davamix.pillsreminder.probe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.ui.theme.PillsReminderTheme
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

// THROWAWAY, from the screen-gallery skill: gallery.sh copies it into androidTest/…/probe/ for a run and removes it
// afterwards. Never committed. A session's own gallery extends GalleryHarness with one @Test per screen.

/** What a shot captures: the screen's frame, the topmost dialog, or the topmost popup (a dropdown menu). */
enum class Capture { FRAME, DIALOG, POPUP }

/** One state to draw, and what to do before drawing it, such as opening a dialog or scrolling. */
class Shot(
    val name: String,
    val capture: Capture = Capture.FRAME,
    val action: (GalleryHarness.() -> Unit)? = null,
    val content: @Composable () -> Unit,
)

abstract class GalleryHarness {
    @get:Rule
    val compose = createComposeRule()

    private var current: Shot? by mutableStateOf(null)
    private var dark by mutableStateOf(false)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Draws every shot and saves them as sheets, four shots side by side at half size, in the app's cache:
     * `gallery-<screen>-<variant>-<n>.png`, with `gallery-<screen>-names.txt` listing the shots in order.
     *
     * The variants follow the device's own font scale: at 1, `light-1` and `dark-1`; at 2, `large`, in light only; in
     * between, such as the phone's "big" font, `light-1.4` and `dark-1.4` (P7: a layout switch at 1.5 looked right at
     * 1 and 2, and not at 1.4). A font scale set around the content wouldn't reach dialogs, which open in their own
     * window, so gallery.sh sets the system's instead. The frame is the phone's content area (P1), with the system bars
     * outside it.
     */
    fun draw(
        screen: String,
        shots: List<Shot>,
    ) {
        compose.setContent {
            PillsReminderTheme(darkTheme = dark) {
                Box(Modifier.size(392.7.dp, 796.4.dp).consumeWindowInsets(WindowInsets.systemBars).testTag(FRAME_TAG)) {
                    current?.let { shot -> key(shot.name, dark) { shot.content() } }
                }
            }
        }
        // The font scale in tenths: 10 at 1, 14 at the phone's "big", 20 at 2.
        val tenths = (context.resources.configuration.fontScale * 10).roundToInt()
        val scale = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
        val variants = if (tenths >= 20) listOf("large" to false) else listOf("light-$scale" to false, "dark-$scale" to true)
        for ((variant, isDark) in variants) {
            dark = isDark
            val images =
                shots.map { shot ->
                    current = shot
                    compose.waitForIdle()
                    shot.action?.invoke(this)
                    compose.waitForIdle()
                    val node =
                        when (shot.capture) {
                            Capture.FRAME -> compose.onNodeWithTag(FRAME_TAG)
                            Capture.DIALOG -> compose.onAllNodes(isDialog()).onLast()
                            Capture.POPUP -> compose.onAllNodes(isPopup()).onLast()
                        }
                    node.captureToImage().asAndroidBitmap()
                }
            // An earlier run's sheets of this variant would be mixed with these.
            context.cacheDir.listFiles { f -> f.name.startsWith("gallery-$screen-$variant-") }?.forEach { it.delete() }
            images.chunked(4).forEachIndexed { index, chunk -> save(sheet(chunk), "gallery-$screen-$variant-${index + 1}.png") }
        }
        File(context.cacheDir, "gallery-$screen-names.txt").writeText(shots.mapIndexed { i, s -> "${i + 1} ${s.name}" }.joinToString("\n"))
    }

    /** Clicks the last node with exactly this text, such as a dialog's button over the screen's. */
    fun click(text: String) {
        compose.onAllNodesWithText(text).onLast().performClick()
    }

    private fun sheet(images: List<Bitmap>): Bitmap {
        val scale = 0.5f
        val gap = 16
        val width = images.maxOf { it.width } * scale
        val height = images.maxOf { it.height } * scale
        val out = Bitmap.createBitmap((width * images.size + gap * (images.size - 1)).toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // Magenta between and around the shots, so a dialog's or a popup's own edges show.
        canvas.drawColor(android.graphics.Color.rgb(255, 0, 255))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        images.forEachIndexed { i, image ->
            val left = i * (width + gap)
            canvas.drawBitmap(image, null, RectF(left, 0f, left + image.width * scale, image.height * scale), paint)
        }
        return out
    }

    private fun save(
        bitmap: Bitmap,
        name: String,
    ) {
        FileOutputStream(File(context.cacheDir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val FRAME_TAG = "gallery-frame"
    }
}
