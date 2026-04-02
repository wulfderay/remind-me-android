package com.wulfderay.remindme.ui

import java.util.Calendar
import java.util.TimeZone

private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

internal fun datePickerSelectionMillis(
    alarmTimeMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val localCalendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = alarmTimeMillis
    }

    return Calendar.getInstance(utcTimeZone).apply {
        clear()
        set(
            localCalendar.get(Calendar.YEAR),
            localCalendar.get(Calendar.MONTH),
            localCalendar.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun mergePickedDateWithExistingTime(
    selectedDateUtcMillis: Long,
    existingAlarmTimeMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val selectedDate = Calendar.getInstance(utcTimeZone).apply {
        timeInMillis = selectedDateUtcMillis
    }

    return Calendar.getInstance(timeZone).apply {
        timeInMillis = existingAlarmTimeMillis
        set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
        set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}