package app.pingu.messages.domain.model

/**
 * A SIM the device can send from.
 *
 * Populated from `SubscriptionManager`. On single-SIM devices, or when the phone-state permission
 * has not been granted, the app works with an empty list and lets the platform pick the default
 * subscription, which is exactly what a single-SIM user expects.
 */
data class SimCard(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String?,
    val phoneNumber: String?,
    /** ARGB colour the system associates with the SIM, used for the composer chip. */
    val colorArgb: Int?,
) {
    val label: String
        get() = displayName.ifBlank { carrierName.orEmpty().ifBlank { "SIM ${slotIndex + 1}" } }
}
