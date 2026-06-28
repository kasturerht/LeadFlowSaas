package com.nexaleads.app.utils

object PhoneUtils {
    /**
     * Sanitizes a phone number by stripping all non-digit characters.
     * Optionally removes the country code prefix (+91 or 0) for consistent DB storage and comparison.
     */
    fun sanitizePhoneNumber(phone: String): String {
        // Remove everything except digits
        var pureNum = phone.replace(Regex("[^0-9]"), "")
        
        // Remove standard Indian country code prefixes if they exist
        if (pureNum.startsWith("91") && pureNum.length > 10) {
            pureNum = pureNum.removePrefix("91")
        } else if (pureNum.startsWith("0") && pureNum.length > 10) {
            pureNum = pureNum.removePrefix("0")
        }
        
        return pureNum
    }
}
