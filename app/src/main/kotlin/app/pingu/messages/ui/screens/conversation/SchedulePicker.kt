package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.core.time.ScheduleLabels
import app.pingu.messages.core.time.SchedulePresets
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The scheduling dialog.
 *
 * Presets first, because "this evening" and "tomorrow morning" cover most of what people schedule,
 * and the full date and time pickers underneath for everything else. A time in the past is rejected
 * with an explanation rather than silently sent immediately.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchedulePickerDialog(
    onSchedule: (Long) -> Unit,
    onDismiss: () -> Unit,
    exactAlarmsAvailable: Boolean,
    onRequestExactAlarms: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { LocalDateTime.now(zone) }
    var showPickers by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),
    )
    val timePickerState = rememberTimePickerState(
        initialHour = now.hour,
        initialMinute = now.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )

    val presetLabels = ScheduleLabels(
        inOneHour = stringResource(R.string.schedule_preset_hour),
        thisEvening = stringResource(R.string.schedule_preset_evening),
        tomorrowMorning = stringResource(R.string.schedule_preset_tomorrow),
        mondayMorning = stringResource(R.string.schedule_preset_monday),
    )
    val presets = remember(now, presetLabels) { SchedulePresets.forNow(now, presetLabels) }
    val pastMessage = stringResource(R.string.schedule_in_the_past)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!exactAlarmsAvailable) {
                    Text(
                        text = stringResource(R.string.schedule_inexact_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRequestExactAlarms) {
                        Text(stringResource(R.string.schedule_exact_alarm_title))
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        AssistChip(
                            onClick = {
                                onSchedule(preset.epochMillis(zone))
                            },
                            label = { Text(preset.label) },
                        )
                    }
                }

                TextButton(onClick = { showPickers = !showPickers }) {
                    Text(stringResource(R.string.schedule_pick_date))
                }

                if (showPickers) {
                    DatePicker(state = datePickerState, showModeToggle = false)
                    TimePicker(state = timePickerState)
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dateMillis = datePickerState.selectedDateMillis
                    if (dateMillis == null) {
                        error = pastMessage
                        return@TextButton
                    }
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    val chosen = LocalDateTime.of(
                        date,
                        LocalTime.of(timePickerState.hour, timePickerState.minute),
                    )
                    val millis = chosen.atZone(zone).toInstant().toEpochMilli()
                    if (millis <= System.currentTimeMillis()) {
                        error = pastMessage
                    } else {
                        onSchedule(millis)
                    }
                },
            ) {
                Text(stringResource(R.string.schedule_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
