// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/MarkerIconHelper.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper object to create custom BitmapDescriptors for map markers.
 */
object MarkerIconHelper {

    // Removed the standalone createProfileMarkerBitmapDescriptor as it's not used
    // and its logic is integrated into rememberUserMarkerIcon

    // Helper function to convert a drawable resource to a Bitmap
    private fun drawableToBitmap(context: Context, @DrawableRes drawableId: Int, sizePx: Int): Bitmap {
        val drawable = context.resources.getDrawable(drawableId, null)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * A composable function to easily get a remembered BitmapDescriptor for a user's marker.
     * @param profileImageUrl The URL of the user's profile image.
     * @param defaultProfileResId A drawable resource ID for a default profile image.
     * @param markerPinResId An optional drawable resource ID for the base pin shape.
     */
    @Composable
    fun rememberUserMarkerIcon(
        profileImageUrl: String?,
        @DrawableRes defaultProfileResId: Int, // e.g., R.drawable.default_profile_pic
        @DrawableRes markerPinResId: Int? = null // e.g., R.drawable.pin_base_shape
    ): BitmapDescriptor? {
        val context = LocalContext.current
        var bitmapDescriptor by remember { mutableStateOf<BitmapDescriptor?>(null) }

        LaunchedEffect(profileImageUrl, defaultProfileResId, markerPinResId) {
            val imageLoader = ImageLoader(context)
            val profileBitmapSize = 96 // Target size for the profile image part of the marker
            val pinBaseSize = 120 // Target size for the overall marker bitmap

            val profileBitmap: Bitmap? = try {
                val request = ImageRequest.Builder(context)
                    .data(profileImageUrl) // <--- CORRECTED THIS LINE
                    .size(profileBitmapSize, profileBitmapSize)
                    .allowHardware(false) // Essential for drawing to bitmap
                    .transformations(CircleCropTransformation())
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    result.drawable.toBitmap(profileBitmapSize, profileBitmapSize)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            // Create the final combined bitmap
            val finalMarkerBitmap = Bitmap.createBitmap(pinBaseSize, pinBaseSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalMarkerBitmap)

            // 1. Draw the base pin shape if provided
            markerPinResId?.let {
                val pinDrawable = context.resources.getDrawable(it, null)
                pinDrawable.setBounds(0, 0, pinBaseSize, pinBaseSize) // Fill the canvas
                pinDrawable.draw(canvas)
            } ?: run {
                // If no pin base, draw a simple circle placeholder if no pin is provided
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY }
                canvas.drawCircle(pinBaseSize / 2f, pinBaseSize / 2f, pinBaseSize / 2f, paint)
            }


            // 2. Draw the profile image (circular) on top of the pin
            val profileBitmapToDraw = profileBitmap ?: drawableToBitmap(context, defaultProfileResId, profileBitmapSize)

            // Positioning the profile image at the top-center of the pin (adjust offsets as needed)
            val profileX = (pinBaseSize - profileBitmapToDraw.width) / 2f
            val profileY = 2f // A small offset from the top (adjust this based on your pin design)
            canvas.drawBitmap(profileBitmapToDraw, profileX, profileY, null)

            bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(finalMarkerBitmap)
        }
        return bitmapDescriptor
    }
}

// Extension function to convert Drawable to Bitmap (for internal use)
fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}