package app.pingu.messages.platform.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot location, turned into a map link.
 *
 * Uses the platform [LocationManager] rather than Play Services, so the app has no dependency on
 * Google's proprietary libraries and works on any Android device including those without Google
 * services at all.
 *
 * The location is read once, formatted into a link and handed to the composer. It is never stored,
 * never tracked over time, and there is no live-location feature pretending to be one: SMS and MMS
 * have no channel to keep a position updated.
 */
class LocationSharing(private val context: Context) {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Whether location services are switched on at all.
     *
     * `isLocationEnabled` only exists from Android 9, so older versions are asked the older
     * question: is either provider the app can actually use turned on.
     */
    fun isLocationEnabled(): Boolean = try {
        val manager = locationManager
        when {
            manager == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> manager.isLocationEnabled
            else -> manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (error: Exception) {
        false
    }

    /** A single fix, or null when it is unavailable within the timeout. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMillis: Long = TIMEOUT_MILLIS): Location? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null

        // A recent cached fix avoids waking the GPS for something the user wants immediately.
        lastKnownLocation(manager)?.let { cached ->
            if (System.currentTimeMillis() - cached.time < CACHE_MAX_AGE_MILLIS) return cached
        }

        return try {
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val listener = android.location.LocationListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    val provider = bestProvider(manager)
                    if (provider == null) {
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    continuation.invokeOnCancellation {
                        runCatching { manager.removeUpdates(listener) }
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            null
        } catch (error: SecurityException) {
            Log.d(TAG, "Location permission was revoked mid-request", error)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(manager: LocationManager): Location? = try {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (error: SecurityException) {
        null
    }

    private fun bestProvider(manager: LocationManager): String? = try {
        val enabled = manager.getProviders(true)
        when {
            LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            else -> enabled.firstOrNull()
        }
    } catch (error: Exception) {
        null
    }

    /**
     * A message body for a location.
     *
     * A plain https link is used rather than a `geo:` URI because it opens on every phone,
     * including an iPhone, and is readable as text when it does not.
     */
    fun formatShareText(location: Location): String {
        val latitude = String.format(Locale.US, "%.6f", location.latitude)
        val longitude = String.format(Locale.US, "%.6f", location.longitude)
        return "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"
    }

    private companion object {
        const val TAG = "LocationSharing"
        const val TIMEOUT_MILLIS = 12_000L
        const val CACHE_MAX_AGE_MILLIS = 60_000L
    }
}
