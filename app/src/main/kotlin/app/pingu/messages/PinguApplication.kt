package app.pingu.messages

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pingu.messages.di.AppContainer
import app.pingu.messages.platform.notification.NotificationChannels
import app.pingu.messages.platform.scheduling.MaintenanceWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Startup does as little as possible on the main thread: the container is lazy, so nothing beyond
 * creating a coroutine scope happens here. The first sync, the contact index and the background
 * workers are all started off the main thread, and only when the app actually holds the SMS role -
 * an app that is not the default SMS app has nothing to sync and no business asking.
 */
class PinguApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unhandled error in an application-scope coroutine", error)
        },
    )

    val container: AppContainer by lazy { AppContainer(this, applicationScope) }

    /**
     * WorkManager is configured here rather than through its default initializer, which the
     * manifest removes. That keeps its initialisation off the critical path of every process
     * start, including the short-lived ones a broadcast receiver creates.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    /**
     * The shared image loader.
     *
     * Animated GIFs and video thumbnails both need explicit decoders. The memory cache is kept
     * modest and the disk cache small, because attachments already live in the telephony provider
     * and a second full copy of every photo would be wasteful on a phone that is short of space.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(IMAGE_MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image-cache"))
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)

        applicationScope.launch {
            container.contactIndex.start()

            if (container.defaultSmsAppManager.isDefault()) {
                warmUp()
            }
            schedulePeriodicMaintenance()
        }
    }

    /** First-run work: mirror the telephony provider and re-arm anything that was queued. */
    private suspend fun warmUp() {
        runCatching { container.syncRepository.syncAll() }
            .onFailure { Log.w(TAG, "Initial sync failed", it) }
        runCatching { container.blockedNumberRepository.importSystemBlockList() }
            .onFailure { Log.d(TAG, "System block list unavailable", it) }
        runCatching { container.scheduledMessageScheduler.rescheduleAll() }
            .onFailure { Log.w(TAG, "Could not re-arm scheduled messages", it) }
        runCatching {
            container.conversationShortcutManager.publishRecent(
                container.conversationRepository.recent(SHORTCUT_LIMIT),
            )
        }.onFailure { Log.d(TAG, "Could not publish conversation shortcuts", it) }
        container.widgetUpdater.requestUpdate()
    }

    private fun schedulePeriodicMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(
            MAINTENANCE_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                MAINTENANCE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.onFailure { Log.w(TAG, "Could not schedule maintenance", it) }
    }

    private companion object {
        const val TAG = "PinguApplication"
        const val MAINTENANCE_WORK_NAME = "pingu-maintenance"
        const val MAINTENANCE_INTERVAL_HOURS = 12L
        const val SHORTCUT_LIMIT = 8
        const val IMAGE_MEMORY_CACHE_FRACTION = 0.20
        const val IMAGE_DISK_CACHE_BYTES = 48L * 1024 * 1024
    }
}
