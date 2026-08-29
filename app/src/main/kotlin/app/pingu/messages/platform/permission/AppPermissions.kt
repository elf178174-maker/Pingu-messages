package app.pingu.messages.platform.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runtime permissions, grouped by the moment they are actually needed.
 *
 * Nothing is requested at startup. Contacts is asked for the first time a conversation would show a
 * name, the microphone the first time the record button is held, location the first time the user
 * taps "Location". Each group carries its own explanation, because a permission dialog with no
 * context is a permission dialog people decline.
 */
enum class PermissionGroup(val permissions: List<String>) {

    /** Granted together with the default SMS role; never requested on its own. */
    MESSAGING(
        listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
        ),
    ),

    CONTACTS(listOf(Manifest.permission.READ_CONTACTS)),

    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        },
    ),

    MICROPHONE(listOf(Manifest.permission.RECORD_AUDIO)),

    LOCATION(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    ),

    PHONE_STATE(listOf(Manifest.permission.READ_PHONE_STATE)),

    /**
     * Writing an attachment into the public Downloads folder.
     *
     * Android 10 made this a MediaStore operation that needs no permission at all, so on those
     * versions the group is empty and no request is ever made.
     */
    SAVE_TO_DOWNLOADS(
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyList()
        },
    ),
}

/** Reads permission state; requesting is done from the UI with the activity result APIs. */
class AppPermissions(private val context: Context) {

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isGranted(group: PermissionGroup): Boolean =
        group.permissions.isEmpty() || group.permissions.all(::isGranted)

    /** True when at least one permission in the group is granted (location, media). */
    fun isPartiallyGranted(group: PermissionGroup): Boolean =
        group.permissions.isEmpty() || group.permissions.any(::isGranted)

    fun missing(group: PermissionGroup): List<String> =
        group.permissions.filterNot(::isGranted)

    /** The app's own settings page, for when a permission was permanently denied. */
    fun appSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
