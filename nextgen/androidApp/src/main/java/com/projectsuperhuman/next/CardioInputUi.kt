package com.projectsuperhuman.next

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val cardioStorageDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal val cardioDecimalKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
internal val cardioIntegerKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)

@Composable
internal fun CardioDateTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Finished"
) {
    val context = LocalContext.current
    val parsed = runCatching {
        LocalDateTime.parse(value.trim(), cardioStorageDateTimeFormatter)
    }.getOrElse { LocalDateTime.now() }
    val visibleFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    val visible = runCatching { parsed.format(visibleFormatter) }.getOrElse { value }

    fun openPicker() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val afterDate = LocalDateTime.of(
                    year,
                    month + 1,
                    day,
                    parsed.hour,
                    parsed.minute
                )
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onValueChange(
                            afterDate
                                .withHour(hour)
                                .withMinute(minute)
                                .withSecond(0)
                                .withNano(0)
                                .format(cardioStorageDateTimeFormatter)
                        )
                    },
                    parsed.hour,
                    parsed.minute,
                    true
                ).show()
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(14.dp))
            .semantics {
                role = Role.Button
                contentDescription = "$label date and time, $visible. Double tap to change."
            }
            .clickable { openPicker() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = superhumanTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                visible,
                color = superhumanTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "CHANGE",
            color = superhumanGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}
