package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Modern, unified Bauhaus Date & Time Picker Modal.
 * Replaces legacy system dialogs with an integrated Compose sheet where users can
 * select both deadline date and time together in one intuitive flow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluxDateTimePickerModal(
    initialDate: LocalDate? = null,
    initialTime: LocalTime? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (selectedDate: LocalDate, selectedTime: LocalTime?) -> Unit,
    onClear: (() -> Unit)? = null
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate ?: today) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    var hasSpecificTime by remember { mutableStateOf(initialTime != null) }
    var selectedHour by remember { mutableIntStateOf(initialTime?.hour ?: 18) }
    var selectedMinute by remember { mutableIntStateOf(initialTime?.minute ?: 0) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(FluxPalette.Paper)
                .border(2.dp, FluxPalette.Void, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DEADLINE & REMINDER",
                            style = FluxTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = FluxPalette.Void
                        )
                        Text(
                            text = "Select date and time collectively",
                            style = FluxTheme.typography.bodySmall,
                            color = FluxPalette.CharcoalMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FluxPalette.Surface)
                            .border(1.dp, FluxPalette.Void, CircleShape)
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            style = FluxTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = FluxPalette.Void
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick 1-Tap Presets
                Text(
                    text = "QUICK PRESETS",
                    style = FluxTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = FluxPalette.Void
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Today 6 PM
                    PresetChip(
                        label = "Today 6 PM",
                        onClick = {
                            selectedDate = today
                            currentYearMonth = YearMonth.from(today)
                            hasSpecificTime = true
                            selectedHour = 18
                            selectedMinute = 0
                        }
                    )
                    // Tomorrow 9 AM
                    PresetChip(
                        label = "Tomorrow 9 AM",
                        onClick = {
                            val tomorrow = today.plusDays(1)
                            selectedDate = tomorrow
                            currentYearMonth = YearMonth.from(tomorrow)
                            hasSpecificTime = true
                            selectedHour = 9
                            selectedMinute = 0
                        }
                    )
                    // This Weekend
                    PresetChip(
                        label = "This Weekend",
                        onClick = {
                            val weekend = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                            selectedDate = weekend
                            currentYearMonth = YearMonth.from(weekend)
                            hasSpecificTime = true
                            selectedHour = 10
                            selectedMinute = 0
                        }
                    )
                    // Next Week Mon
                    PresetChip(
                        label = "Next Mon 9 AM",
                        onClick = {
                            val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                            selectedDate = nextMon
                            currentYearMonth = YearMonth.from(nextMon)
                            hasSpecificTime = true
                            selectedHour = 9
                            selectedMinute = 0
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = FluxPalette.Surface, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Calendar Month Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
                        style = FluxTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = FluxPalette.Void
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, FluxPalette.Void, RoundedCornerShape(8.dp))
                                .clickable { currentYearMonth = currentYearMonth.minusMonths(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◀", fontSize = 11.sp, color = FluxPalette.Void)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, FluxPalette.Void, RoundedCornerShape(8.dp))
                                .clickable { currentYearMonth = currentYearMonth.plusMonths(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", fontSize = 11.sp, color = FluxPalette.Void)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Weekday Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            style = FluxTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = FluxPalette.CharcoalMuted,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Calendar Day Grid
                val firstDayOfMonth = currentYearMonth.atDay(1)
                val daysInMonth = currentYearMonth.lengthOfMonth()
                // DayOfWeek 1 = Mon ... 7 = Sun
                val leadingEmptyDays = firstDayOfMonth.dayOfWeek.value - 1
                val totalSlots = leadingEmptyDays + daysInMonth
                val rows = (totalSlots + 6) / 7

                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (col in 0 until 7) {
                            val slotIndex = row * 7 + col
                            val dayNumber = slotIndex - leadingEmptyDays + 1
                            if (dayNumber in 1..daysInMonth) {
                                val cellDate = currentYearMonth.atDay(dayNumber)
                                val isSelected = cellDate == selectedDate
                                val isToday = cellDate == today
                                val isPast = cellDate.isBefore(today)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSelected -> FluxPalette.Signal
                                                isToday -> FluxPalette.Lavender.copy(alpha = 0.4f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else if (isToday) 1.dp else 0.dp,
                                            color = if (isSelected || isToday) FluxPalette.Void else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDate = cellDate },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        style = FluxTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Normal
                                        ),
                                        color = if (isPast && !isSelected) FluxPalette.CharcoalMuted else FluxPalette.Void
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = FluxPalette.Surface, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Time Configuration (Integrated on Same Screen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TIME OF DAY",
                            style = FluxTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = FluxPalette.Void
                        )
                        Text(
                            text = if (hasSpecificTime) {
                                LocalTime.of(selectedHour, selectedMinute).format(DateTimeFormatter.ofPattern("h:mm a"))
                            } else {
                                "Off — due at end of day (23:59)"
                            },
                            style = FluxTheme.typography.bodySmall,
                            color = FluxPalette.CharcoalMuted
                        )
                    }

                    FluxSwitch(
                        checked = hasSpecificTime,
                        onCheckedChange = { hasSpecificTime = it },
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle specific time"
                        }
                    )
                }

                if (hasSpecificTime) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Common Time Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            9 to 0 to "9:00 AM",
                            12 to 0 to "12:00 PM",
                            15 to 0 to "3:00 PM",
                            18 to 0 to "6:00 PM",
                            21 to 0 to "9:00 PM"
                        ).forEach { (timePair, label) ->
                            val (hour, minute) = timePair
                            val isSelected = selectedHour == hour && selectedMinute == minute
                            TimeChip(
                                label = label,
                                isSelected = isSelected,
                                onClick = {
                                    selectedHour = hour
                                    selectedMinute = minute
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Precise Hour / Minute Stepper Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FluxPalette.Surface)
                            .border(1.dp, FluxPalette.Void, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour Control (12-hour format display)
                        val isPm = selectedHour >= 12
                        val displayHour = when {
                            selectedHour == 0 -> 12
                            selectedHour > 12 -> selectedHour - 12
                            else -> selectedHour
                        }

                        StepperItem(
                            label = "Hour",
                            value = "%02d".format(displayHour),
                            onDecrement = {
                                val newDisplay = if (displayHour == 1) 12 else displayHour - 1
                                selectedHour = if (isPm) (if (newDisplay == 12) 12 else newDisplay + 12) else (if (newDisplay == 12) 0 else newDisplay)
                            },
                            onIncrement = {
                                val newDisplay = if (displayHour == 12) 1 else displayHour + 1
                                selectedHour = if (isPm) (if (newDisplay == 12) 12 else newDisplay + 12) else (if (newDisplay == 12) 0 else newDisplay)
                            }
                        )

                        Text(":", style = FluxTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))

                        // Minute Control
                        StepperItem(
                            label = "Min",
                            value = "%02d".format(selectedMinute),
                            onDecrement = { selectedMinute = (selectedMinute - 5 + 60) % 60 },
                            onIncrement = { selectedMinute = (selectedMinute + 5) % 60 }
                        )

                        // AM / PM Toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPm) FluxPalette.Lavender else FluxPalette.Signal)
                                .border(1.dp, FluxPalette.Void, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedHour = if (isPm) selectedHour - 12 else selectedHour + 12
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPm) "PM" else "AM",
                                style = FluxTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = FluxPalette.Void
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onClear != null) {
                        FluxButton(
                            text = "Clear",
                            onClick = {
                                onClear()
                                onDismissRequest()
                            },
                            variant = FluxButtonVariant.Ghost,
                            modifier = Modifier.weight(0.7f),
                            minWidth = 0.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }

                    FluxButton(
                        text = "Set Reminder",
                        onClick = {
                            val time = if (hasSpecificTime) LocalTime.of(selectedHour, selectedMinute) else null
                            onConfirm(selectedDate, time)
                            onDismissRequest()
                        },
                        variant = FluxButtonVariant.Primary,
                        modifier = Modifier.weight(1.3f),
                        minWidth = 0.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FluxPalette.Surface)
            .border(1.dp, FluxPalette.Void, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FluxTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = FluxPalette.Void
        )
    }
}

@Composable
private fun TimeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) FluxPalette.Signal else FluxPalette.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = FluxPalette.Void,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FluxTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = FluxPalette.Void
        )
    }
}

@Composable
private fun StepperItem(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = FluxTheme.typography.labelSmall, color = FluxPalette.CharcoalMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(FluxPalette.Surface)
                    .border(1.dp, FluxPalette.Void, CircleShape)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontWeight = FontWeight.Bold, color = FluxPalette.Void)
            }
            Text(
                text = value,
                style = FluxTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FluxPalette.Void,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(FluxPalette.Surface)
                    .border(1.dp, FluxPalette.Void, CircleShape)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontWeight = FontWeight.Bold, color = FluxPalette.Void)
            }
        }
    }
}
