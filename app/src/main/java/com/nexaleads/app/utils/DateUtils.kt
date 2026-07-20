package com.nexaleads.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    fun isExhaustionDateWithin7Days(exhaustionDateStr: String?): Boolean {
        if (exhaustionDateStr.isNullOrEmpty()) return false

        try {
            val targetTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                add(Calendar.DAY_OF_YEAR, 7)
            }.timeInMillis

            var parsedDate: Date? = null
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd",
                "dd-MM-yyyy"
            )

            for (f in formats) {
                try {
                    val sdf = SimpleDateFormat(f, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    parsedDate = sdf.parse(exhaustionDateStr)
                    if (parsedDate != null) break
                } catch (e: Exception) {
                    // Try next format
                }
            }

            if (parsedDate != null) {
                return parsedDate.time <= targetTime
            }

            // Fallback to strict string comparison if all parsing fails
            val fallbackIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val calTarget = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                add(Calendar.DAY_OF_YEAR, 7)
            }
            return exhaustionDateStr <= fallbackIso.format(calTarget.time)
        } catch (e: Exception) {
            return false
        }
    }
}
