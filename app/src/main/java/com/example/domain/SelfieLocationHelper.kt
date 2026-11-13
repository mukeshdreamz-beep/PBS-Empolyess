package com.example.domain

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

object SelfieLocationHelper {

    private suspend fun <T> Task<T>.awaitNullable(timeoutMs: Long): T? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(task.result)
                } else {
                    cont.resumeWithException(task.exception ?: IllegalStateException("Location task failed"))
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<CapturedLocation> {
        val appContext = context.applicationContext
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val cts = CancellationTokenSource()
        return try {
            val fresh = try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .awaitNullable(8_000L)
            } catch (_: Exception) {
                null
            }
            val location = fresh ?: try {
                client.lastLocation.awaitNullable(3_000L)
            } catch (_: Exception) {
                null
            }

            if (location == null) {
                Result.failure(IllegalStateException("Unable to detect GPS location. Enable Location/GPS and try again."))
            } else {
                val address = resolveAddress(appContext, location.latitude, location.longitude)
                Result.success(
                    CapturedLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address
                    )
                )
            }
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            cts.cancel()
        }
    }

    private suspend fun resolveAddress(context: Context, lat: Double, lng: Double): String {
        val coordFallback = formatCoords(lat, lng)
        if (!Geocoder.isPresent()) return coordFallback

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(3_000L) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1) { list ->
                            cont.resume(list)
                        }
                    }
                }.orEmpty()
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1).orEmpty()
                }
            }
            val first = addresses.firstOrNull()
            if (first != null) {
                val parts = listOfNotNull(
                    first.subLocality?.takeIf { it.isNotBlank() } ?: first.featureName?.takeIf { it.isNotBlank() },
                    first.thoroughfare?.takeIf { it.isNotBlank() },
                    first.locality?.takeIf { it.isNotBlank() },
                    first.adminArea?.takeIf { it.isNotBlank() }
                ).distinct()
                val formatted = parts.joinToString(", ").ifBlank {
                    first.getAddressLine(0).orEmpty()
                }
                formatted.ifBlank { coordFallback }.take(280)
            } else {
                coordFallback
            }
        } catch (_: Exception) {
            coordFallback
        }
    }

    fun formatCoords(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    fun encodeImageProxyToBase64(image: ImageProxy): String {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return ""

        val rotation = image.imageInfo.rotationDegrees.toFloat()
        val maxSide = 320f
        val scale = minOf(maxSide / decoded.width, maxSide / decoded.height, 1f)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0f) {
                postRotate(rotation)
            }
            // Mirror front camera horizontally so text/faces look natural
            postScale(-1f, 1f)
        }

        val processed = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        )
        if (processed !== decoded) {
            decoded.recycle()
        }

        val baos = ByteArrayOutputStream()
        var quality = 70
        processed.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > 120_000 && quality > 35) {
            baos.reset()
            quality -= 10
            processed.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        processed.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    fun openLocationInMaps(context: Context, lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label.ifBlank { "PBS Attendance Location" })
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            context.startActivity(geoIntent)
        } catch (_: Exception) {
            val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
        }
    }
}
