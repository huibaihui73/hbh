package com.quntodo.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun pickDateTime(
    context: Context,
    initial: Long?,
    dateOnly: Boolean,
    onPicked: (at: Long, hasTime: Boolean) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initialDateTime = initial?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime()
    } ?: LocalDateTime.now().withSecond(0).withNano(0)

    DatePickerDialog(
        context,
        { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            if (dateOnly) {
                onPicked(
                    date.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli(),
                    false
                )
            } else {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onPicked(
                            date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
                            true
                        )
                    },
                    initialDateTime.hour,
                    initialDateTime.minute,
                    true
                ).apply { setTitle("选择时间") }.show()
            }
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth
    ).apply { setTitle(if (dateOnly) "选择日期" else "选择日期和时间") }.show()
}
