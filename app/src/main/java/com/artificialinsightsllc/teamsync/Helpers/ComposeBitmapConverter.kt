// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/ComposeBitmapConverter.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper object to convert a Compose Composable to a Bitmap.
 * This is useful for rendering Compose UI into Bitmaps for contexts like Google Maps custom info windows.
 */
object ComposeBitmapConverter {

    /**
     * Renders a Composable into a Bitmap.
     * This function creates a temporary ComposeView, sets the content, measures and lays it out,
     * and then draws it to a Bitmap.
     *
     * @param context The application context.
     * @param content The Composable content to render.
     * @return A Bitmap representing the rendered Composable, or null if rendering fails.
     */
    suspend fun getComposeBitmap(context: Context, content: @Composable () -> Unit): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            val composeView = ComposeView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // Set the Composable content
                setContent {
                    content()
                }
            }

            // Add the ComposeView to a temporary ViewGroup to ensure it's attached to a window
            // This is often necessary for it to be properly measured and drawn.
            val tempContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(composeView)
            }

            // Measure and layout the ComposeView
            tempContainer.doOnLayout { view ->
                try {
                    val bitmap = Bitmap.createBitmap(
                        view.width,
                        view.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    view.draw(canvas) // Draw the view hierarchy onto the bitmap
                    continuation.resume(bitmap)
                } catch (e: Exception) {
                    continuation.resume(null) // Resume with null on error
                } finally {
                    // Clean up: remove the temporary view from its parent
                    (view.parent as? ViewGroup)?.removeView(view)
                }
            }

            // If the coroutine is cancelled, ensure cleanup
            continuation.invokeOnCancellation {
                (composeView.parent as? ViewGroup)?.removeView(composeView)
            }
        }
    }
}