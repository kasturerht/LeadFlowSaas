package com.nexaleads.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class CallLogEntry(
    val number: String,
    val name: String?,
    val timestamp: Long,
    val type: Int
)

fun getRecentCallLogs(context: Context, limit: Int = 10): List<CallLogEntry> {
    val entries = mutableListOf<CallLogEntry>()
    
    // 1. Fetch Native Call Logs
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )
            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                var count = 0
                while (it.moveToNext() && count < limit) {
                    entries.add(
                        CallLogEntry(
                            number = it.getString(numberIndex) ?: "",
                            name = it.getString(nameIndex),
                            timestamp = it.getLong(dateIndex),
                            type = it.getInt(typeIndex)
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    // 3. Merge, Sort and Limit
    return entries.sortedByDescending { it.timestamp }.take(limit)
}
