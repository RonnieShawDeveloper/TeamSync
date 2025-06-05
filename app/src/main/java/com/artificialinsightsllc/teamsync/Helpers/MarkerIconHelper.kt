// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/MarkerIconHelper.kt
package com.artificialinsightsllc.teamsync.Helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Models.MapMarkerType
import androidx.compose.ui.graphics.Color // Import Compose Color
import androidx.compose.ui.graphics.toArgb // Import toArgb extension function

/**
 * Helper object to create custom BitmapDescriptors for map markers.
 */
object MarkerIconHelper {

    // Helper function to convert a drawable resource to a Bitmap
    private fun drawableToBitmap(context: Context, @DrawableRes drawableId: Int, sizePx: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId) // Use ContextCompat
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
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
                    .data(profileImageUrl)
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
                val pinDrawable = ContextCompat.getDrawable(context, it) // Use ContextCompat for drawable
                pinDrawable?.setBounds(0, 0, pinBaseSize, pinBaseSize) // Fill the canvas
                pinDrawable?.draw(canvas)
            } ?: run {
                // If no pin base, draw a simple circle placeholder if no pin is provided
                val paint = Paint().apply { color = android.graphics.Color.GRAY } // Use android.graphics.Color here
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

    /**
     * A composable function to get a remembered BitmapDescriptor for a MapMarker.
     * Creates a semi-transparent orange circle with a black icon (chat/camera) and an optional directional arrow.
     * @param markerType The type of the map marker (CHAT or PHOTO).
     * @param cameraBearing The bearing of the camera for PHOTO markers (0-360 degrees), nullable.
     */
    @Composable
    fun rememberMapMarkerIcon(
        markerType: MapMarkerType,
        cameraBearing: Float? = null
    ): BitmapDescriptor? {
        val context = LocalContext.current
        var bitmapDescriptor by remember { mutableStateOf<BitmapDescriptor?>(null) }

        LaunchedEffect(markerType, cameraBearing) {
            val iconSize = 100 // Diameter of the circle marker
            val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Draw the semi-transparent orange circle background
            val circlePaint = Paint().apply {
                // Use Compose Color for manipulation, then convert to Android Color Int
                color = Color(0xFFFFA500).copy(alpha = 0.7f).toArgb() // Semi-transparent orange
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val strokePaint = Paint().apply {
                color = android.graphics.Color.BLACK // Use android.graphics.Color here
                style = Paint.Style.STROKE
                strokeWidth = 4f // Black stroke border
                isAntiAlias = true
            }
            val centerX = iconSize / 2f
            val centerY = iconSize / 2f
            val radius = iconSize / 2f - strokePaint.strokeWidth / 2 // Adjust radius for stroke
            canvas.drawCircle(centerX, centerY, radius, circlePaint)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)

            // 2. Draw the appropriate icon (chat or camera) in the center
            val iconDrawableRes = when (markerType) {
                MapMarkerType.CHAT -> R.drawable.ic_chat_bubble // You'll need to create this drawable
                MapMarkerType.PHOTO -> R.drawable.ic_camera // You'll need to create this drawable
            }

            val iconDrawable = ContextCompat.getDrawable(context, iconDrawableRes)
            iconDrawable?.let {
                val iconPadding = 20 // Padding from the edge of the circle
                val iconLeft = (iconSize - (iconSize - iconPadding * 2)) / 2
                val iconTop = (iconSize - (iconSize - iconPadding * 2)) / 2
                val iconRight = iconSize - iconLeft
                val iconBottom = iconSize - iconTop

                it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                it.setTint(android.graphics.Color.BLACK) // Black icon // Use android.graphics.Color here
                it.draw(canvas)
            }

            // 3. Draw the directional arrow for PHOTO markers if bearing is available
            if (markerType == MapMarkerType.PHOTO && cameraBearing != null) {
                val arrowDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up) // You'll need an arrow drawable
                arrowDrawable?.let {
                    canvas.save() // Save current canvas state

                    // Translate to center of marker
                    canvas.translate(centerX, centerY)
                    // Rotate by the camera bearing
                    canvas.rotate(cameraBearing)

                    // Position arrow slightly outside the circle, pointing upwards initially
                    val arrowWidth = 20
                    val arrowHeight = 30
                    val arrowOffsetFromCenter = radius + 10 // Distance from marker center to arrow base

                    it.setBounds(
                        -arrowWidth / 2, // Left
                        -arrowHeight / 2 - arrowOffsetFromCenter.toInt(), // Top (negative to place it above center)
                        arrowWidth / 2, // Right
                        arrowHeight / 2 - arrowOffsetFromCenter.toInt() // Bottom
                    )
                    it.setTint(android.graphics.Color.BLACK) // Black arrow // Use android.graphics.Color here
                    it.draw(canvas)

                    canvas.restore() // Restore canvas to original state
                }
            }

            bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
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
