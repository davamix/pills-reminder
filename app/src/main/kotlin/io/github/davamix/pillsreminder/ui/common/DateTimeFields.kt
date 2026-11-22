package io.github.davamix.pillsreminder.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/** From this font scale on, the pickers open in their input mode: the calendar and the clock dial don't fit (NF-7). */
private const val LARGE_FONT_SCALE = 1.5f

/**
 * A date as a button, which opens Material 3's date picker. The picker works in UTC milliseconds, so the date is
 * converted at UTC, never in the phone's zone, which would shift it by a day (P6 SPEC §4.3.11).
 *
 * The picker writes in the UI's language, like the rest of the app (P6 Q5), with the phone's region for the first day
 * of the week: Monday on a phone in Spain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    today: LocalDate,
    text: DateTimeText,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    LabelledButton(label = label, value = text.date(date, today), onClick = { open = true }, modifier = modifier, icon = true)
    if (!open) return
    val locale = pickerLocale()
    val large = isLargeFont()
    val state =
        remember(locale) {
            DatePickerState(
                locale = locale,
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                initialDisplayMode = if (large) DisplayMode.Input else DisplayMode.Picker,
            )
        }
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        // The picker's own title and headline would follow the phone's locale, not the UI's (P6 Q5): these are the app's.
        DatePicker(
            state = state,
            title = { Text(label, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            headline = {
                val selected = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                Text(
                    text = selected?.let { text.date(it, today) } ?: "",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
        )
    }
}

/** A time of day as a button, which opens Material 3's time picker, in the phone's hour format (NF-8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: LocalTime,
    text: DateTimeText,
    onTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    LabelledButton(label = label, value = text.time(time), onClick = { open = true }, modifier = modifier)
    if (!open) return
    val state =
        rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    val large = isLargeFont()
    TimePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                onTime(LocalTime.of(state.hour, state.minute))
                open = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(label) },
    ) {
        if (large) TimeInput(state = state) else TimePicker(state = state)
    }
}

/** A field's label above an outlined button that shows its value. */
@Composable
private fun LabelledButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            if (icon) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(value)
        }
    }
}

/** Whether the font scale is large enough that pickers open in their input mode, and fields side by side stack (NF-7). */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/** The UI's language, from the resources (P6 Q5), with the phone's region. */
@Composable
private fun pickerLocale(): Locale {
    val language = stringResource(R.string.date_locale)
    val region = LocalConfiguration.current.locales[0].country
    return remember(language, region) {
        Locale
            .Builder()
            .setLanguageTag(language)
            .setRegion(region)
            .build()
    }
}
