package app.pingu.messages.data.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import app.pingu.messages.domain.model.SimCard

/**
 * SIM and subscription information.
 *
 * Dual-SIM support is entirely a matter of using the right `SmsManager`: one created for a specific
 * subscription id sends from that SIM. Everything else - labels, colours, the picker - is
 * presentation on top of what [SubscriptionManager] reports.
 *
 * The list is empty when the phone-state permission has not been granted or when the device has no
 * telephony. Both are normal, and both mean "let the platform choose the default subscription",
 * which is exactly right for a single-SIM phone.
 */
class SimDataSource(private val context: Context) {

    private val subscriptionManager: SubscriptionManager?
        get() = ContextCompat.getSystemService(context, SubscriptionManager::class.java)

    private val telephonyManager: TelephonyManager?
        get() = ContextCompat.getSystemService(context, TelephonyManager::class.java)

    fun hasTelephony(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** True when a SIM is present and usable for sending. */
    fun hasReadySim(): Boolean {
        val manager = telephonyManager ?: return false
        return try {
            manager.simState == TelephonyManager.SIM_STATE_READY
        } catch (error: SecurityException) {
            // Without the permission we cannot tell; assume a SIM so the user is not blocked by a
            // warning that may be wrong.
            true
        }
    }

    fun hasPhoneStateAccess(): Boolean = hasPhoneStatePermission()

    /**
     * Whether the hardware can hold more than one active subscription.
     *
     * This is the one dual-SIM question that can be asked without the phone-state permission, so it
     * is what decides whether the app offers to ask for it at all. A single-SIM phone is never
     * bothered with a permission it would gain nothing from.
     */
    fun supportsMultipleSims(): Boolean = try {
        (subscriptionManager?.activeSubscriptionInfoCountMax ?: 1) > 1
    } catch (error: Exception) {
        false
    }

    /** Every SIM the user can send from. Empty when unknown. */
    fun availableSims(): List<SimCard> {
        if (!hasPhoneStatePermission()) return emptyList()
        val manager = subscriptionManager ?: return emptyList()
        return try {
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString().orEmpty(),
                    carrierName = info.carrierName?.toString(),
                    phoneNumber = runCatching { info.number }.getOrNull()?.takeIf { it.isNotBlank() },
                    colorArgb = info.iconTint,
                )
            }.sortedBy { it.slotIndex }
        } catch (error: SecurityException) {
            emptyList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    val isMultiSim: Boolean get() = availableSims().size > 1

    /** The subscription the platform would use when the app does not specify one. */
    fun defaultSmsSubscriptionId(): Int = try {
        SmsManager.getDefaultSmsSubscriptionId()
    } catch (error: Exception) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    /**
     * An [SmsManager] bound to a subscription, falling back to the default one when the id is not
     * valid. Using the subscription-specific instance is what makes "send from SIM 2" real rather
     * than decorative.
     */
    @Suppress("DEPRECATION")
    fun smsManagerFor(subscriptionId: Int): SmsManager {
        val useSpecific = subscriptionId >= 0 &&
            subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (useSpecific) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(subscriptionId)
                } else {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }
            }.getOrElse { defaultSmsManager() }
        } else {
            defaultSmsManager()
        }
    }

    @Suppress("DEPRECATION")
    fun defaultSmsManager(): SmsManager =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    /** True when the device is currently roaming, used to honour the MMS roaming setting. */
    fun isRoaming(): Boolean = try {
        telephonyManager?.isNetworkRoaming == true
    } catch (error: SecurityException) {
        false
    }
}
