package com.rasticpack.app.ui.customers

/**
 * معادل دقیق parseCoordsFromText در وب — استخراج مختصات (lat,lng) از لینک/متن اشتراک‌گذاری
 * نشان یا گوگل‌مپ یا هر متن حاوی دو عدد اعشاری. چند الگو را به‌ترتیب امتحان می‌کند.
 */
data class ParsedCoords(val lat: Double, val lng: Double)

object LocationParsing {

    private fun isPlausibleIran(a: Double, b: Double) = a in 20.0..45.0 && b in 40.0..65.0

    fun parse(rawText: String?): ParsedCoords? {
        if (rawText.isNullOrBlank()) return null
        val text = rawText.trim()

        // الگوی ?lat=..&lng=.. یا ?lat=..&lon=..
        Regex("[?&]lat=(-?\\d+\\.?\\d*)[^0-9.\\-]+(?:lng|lon)=(-?\\d+\\.?\\d*)", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lng = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) return ParsedCoords(lat, lng)
            }

        // الگوی @lat,lng (گوگل‌مپ)
        Regex("@(-?\\d{1,3}\\.\\d+),(-?\\d{1,3}\\.\\d+)").find(text)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lng = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return ParsedCoords(lat, lng)
        }

        // الگوی q=lat,lng یا ll=lat,lng
        Regex("[?&](?:q|ll)=(-?\\d{1,3}\\.\\d+),(-?\\d{1,3}\\.\\d+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lng = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return ParsedCoords(lat, lng)
        }

        // دو عدد اعشاری جدا شده در هر جای متن
        val nums = Regex("-?\\d{1,3}\\.\\d{3,}").findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (nums.size >= 2) {
            val a = nums[0]; val b = nums[1]
            return when {
                isPlausibleIran(a, b) -> ParsedCoords(a, b)
                isPlausibleIran(b, a) -> ParsedCoords(b, a)
                else -> ParsedCoords(a, b)
            }
        }
        return null
    }

    /** معادل neshanMapUrl در وب */
    fun neshanMapUrl(lat: Double, lng: Double) = "https://neshan.org/maps?lat=$lat&lng=$lng"
}
