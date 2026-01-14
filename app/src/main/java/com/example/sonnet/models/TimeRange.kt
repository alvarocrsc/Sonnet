package com.example.sonnet.models

/**
 * Time range filter for statistics
 */
enum class TimeRange(val days: Int) {
    WEEK(7),        // Last 7 days
    MONTH(30),      // Last 30 days
    SIX_MONTHS(180), // Last 6 months
    ALL_TIME(-1);   // All time (no filter)
    
    /**
     * Get the cutoff timestamp for this time range
     * @return timestamp in milliseconds, or null for ALL_TIME
     */
    fun getCutoffTimestamp(): Long? {
        if (this == ALL_TIME) return null
        val now = System.currentTimeMillis()
        return now - (days * 24L * 60L * 60L * 1000L)
    }
}
