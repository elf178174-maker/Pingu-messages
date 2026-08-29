package app.pingu.messages.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.domain.model.SimCard
import app.pingu.messages.platform.backup.BackupManager
import app.pingu.messages.platform.media.StorageMaintenance
import app.pingu.messages.platform.system.DefaultSmsAppManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sims: List<SimCard> = emptyList(),
    /** True on a dual-SIM device that has not granted the phone-state permission yet. */
    val canAskForSimAccess: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val storage: StorageMaintenance.Usage = StorageMaintenance.Usage(0, 0, 0, 0),
    val busyMessage: String? = null,
    val resultMessage: String? = null,
)

/**
 * Settings.
 *
 * Every toggle writes straight through to the DataStore, so there is no "apply" step and no
 * possibility of the UI and the stored value disagreeing. Backup and storage operations report
 * their outcome through [SettingsUiState.resultMessage], including their failures.
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val sims: SimDataSource,
    private val defaultSmsApp: DefaultSmsAppManager,
    private val storage: StorageMaintenance,
    private val backup: BackupManager,
) : ViewModel() {

    private val simCards = MutableStateFlow<List<SimCard>>(emptyList())
    private val defaultApp = MutableStateFlow(defaultSmsApp.isDefault())
    private val usage = MutableStateFlow(StorageMaintenance.Usage(0, 0, 0, 0))
    private val busy = MutableStateFlow<String?>(null)
    private val result = MutableStateFlow<String?>(null)
    private val simAccess = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        simCards,
        defaultApp,
        usage,
        combine(busy, result, simAccess) { busyMessage, resultMessage, askForSims ->
            Triple(busyMessage, resultMessage, askForSims)
        },
    ) { settings, cards, isDefault, storageUsage, messages ->
        SettingsUiState(
            settings = settings,
            sims = cards,
            canAskForSimAccess = messages.third,
            isDefaultSmsApp = isDefault,
            storage = storageUsage,
            busyMessage = messages.first,
            resultMessage = messages.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SettingsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            simCards.value = runCatching { sims.availableSims() }.getOrDefault(emptyList())
            // Only a dual-SIM phone gains anything from the phone-state permission, so only a
            // dual-SIM phone is ever offered the chance to grant it.
            simAccess.value = runCatching {
                sims.supportsMultipleSims() && !sims.hasPhoneStateAccess()
            }.getOrDefault(false)
            defaultApp.value = defaultSmsApp.isDefault()
            usage.value = runCatching { storage.usage() }
                .getOrDefault(StorageMaintenance.Usage(0, 0, 0, 0))
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun clearCache(message: String) {
        viewModelScope.launch {
            storage.clearCaches()
            usage.value = storage.usage()
            result.value = message
        }
    }

    fun export(target: Uri, includeMessages: Boolean, runningMessage: String, doneTemplate: String, failure: String) {
        viewModelScope.launch {
            busy.value = runningMessage
            val outcome = backup.export(target, includeMessages)
            busy.value = null
            result.value = outcome.fold(
                onSuccess = { summary ->
                    doneTemplate.format(
                        app.pingu.messages.core.util.FileSizes.format(summary.bytesWritten),
                    )
                },
                onFailure = { failure },
            )
        }
    }

    fun import(source: Uri, restoreMessages: Boolean, doneTemplate: String, failure: String) {
        viewModelScope.launch {
            busy.value = null
            val outcome = backup.import(source, restoreMessages && defaultSmsApp.isDefault())
            result.value = outcome.fold(
                onSuccess = { summary ->
                    doneTemplate.format(summary.conversationsRestored, summary.messagesRestored)
                },
                onFailure = { failure },
            )
            refresh()
        }
    }

    fun consumeResult() {
        result.value = null
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
