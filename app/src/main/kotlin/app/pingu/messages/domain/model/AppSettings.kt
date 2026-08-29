package app.pingu.messages.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preset accent used when dynamic colour is off or unavailable. */
enum class AccentColor { BRAND, BLUE, INDIGO, VIOLET, GREEN, AMBER, ROSE }

/** How much of a message the lock screen and other public surfaces may reveal. */
enum class NotificationPrivacy {
    /** Sender and message body. */
    FULL,

    /** Sender name only. */
    SENDER_ONLY,

    /** "New message" with no sender and no content. */
    HIDDEN,

    /** No notification at all while the device is locked. */
    NONE,
}

/** What happens when several recipients are addressed at once. */
enum class GroupMessagingMode {
    /** One MMS addressed to everyone, so replies go to the whole group. */
    GROUP_MMS,

    /** A separate SMS to each recipient, so replies come back privately. */
    INDIVIDUAL_SMS,
}

enum class SwipeAction { NONE, ARCHIVE, DELETE, MARK_READ_UNREAD, PIN, MUTE }

enum class BubbleShape {
    /** Symmetric rounded rectangles. */
    ROUNDED,

    /** A tail on the last bubble of each group, the classic SMS look. */
    TAILED,
}

/**
 * Everything the user can configure. Stored in a DataStore, not in SharedPreferences, and read as
 * a single immutable snapshot so the UI never observes a half-applied change.
 */
data class AppSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: AccentColor = AccentColor.BRAND,
    val pureBlackDarkMode: Boolean = false,
    val bubbleShape: BubbleShape = BubbleShape.TAILED,
    val messageTextScale: Float = 1.0f,

    // Notifications
    val notificationPrivacy: NotificationPrivacy = NotificationPrivacy.FULL,
    val notificationVibrate: Boolean = true,
    val conversationBubbles: Boolean = false,

    // Messaging
    val defaultSubscriptionId: Int = SUBSCRIPTION_ASK,
    val deliveryReports: Boolean = false,
    val autoDownloadMms: Boolean = true,
    val autoDownloadMmsWhileRoaming: Boolean = false,
    val groupMessagingMode: GroupMessagingMode = GroupMessagingMode.GROUP_MMS,
    val splitLongMessages: Boolean = true,
    val sendDelaySeconds: Int = 0,
    val sendMmsReadReports: Boolean = false,
    val reactionTextFallback: Boolean = false,
    val quoteWhenReplying: Boolean = true,

    // Privacy
    val appLockEnabled: Boolean = false,
    val spamFilterEnabled: Boolean = true,

    // Storage
    val autoDeleteMediaDays: Int = 0,

    // List behaviour
    val swipeRightAction: SwipeAction = SwipeAction.ARCHIVE,
    val swipeLeftAction: SwipeAction = SwipeAction.MARK_READ_UNREAD,

    // First run
    val onboardingComplete: Boolean = false,
) {
    companion object {
        /** Sentinel for "ask which SIM every time". */
        const val SUBSCRIPTION_ASK = -1

        /** Longest undo window the composer offers. */
        const val MAX_SEND_DELAY_SECONDS = 15
    }
}
