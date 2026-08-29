package app.pingu.messages.platform.system

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * The default SMS role.
 *
 * Android allows exactly one app to receive `SMS_DELIVER` and to write to the SMS provider. An app
 * cannot grant itself that role; it can only ask, and the user decides in a system dialog. There is
 * no supported way around this and the app does not try to find one - it explains why the role is
 * needed and opens the right system screen.
 *
 * The request mechanism changed in Android 10: [RoleManager] replaced the old
 * `ACTION_CHANGE_DEFAULT` intent. Both are handled here so the flow is a single call for the UI.
 */
class DefaultSmsAppManager(private val context: Context) {

    fun isDefault(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /** The package currently holding the role, for the "currently: Messages" line in onboarding. */
    fun currentDefaultPackage(): String? = Telephony.Sms.getDefaultSmsPackage(context)

    /**
     * Builds the intent that asks the user for the role.
     *
     * Returns null when the device cannot grant it at all (no telephony, or a secondary user
     * profile), which the UI turns into an explanation rather than a dead button.
     */
    fun createRequestIntent(): Intent? {
        if (isDefault()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = ContextCompat.getSystemService(context, RoleManager::class.java)
                ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        @Suppress("DEPRECATION")
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /** True when the role can be requested on this device at all. */
    fun canRequestRole(): Boolean = createRequestIntent() != null || isDefault()

    companion object {
        /** Result code the UI uses when starting the role request. */
        const val REQUEST_CODE = 1001

        fun wasGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
    }
}
