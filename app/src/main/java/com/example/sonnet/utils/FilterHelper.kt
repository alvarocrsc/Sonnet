package com.example.sonnet.utils

import android.content.Context
import android.widget.TextView
import com.example.sonnet.R
import com.example.sonnet.models.TimeRange

/**
 * Helper object to manage time range filter UI
 * Consolidates duplicate logic for filter button states across StatsManager and ProfileManager
 */
object FilterHelper {
    
    /**
     * Updates filter button states based on selected time range
     * @param context Android context for color resources
     * @param timeRange Currently selected time range
     * @param filter7Days TextView for 7 days filter
     * @param filter30Days TextView for 30 days filter
     * @param filter6Months TextView for 6 months filter
     * @param filterAllTime TextView for all time filter
     */
    fun updateFilterSelection(
        context: Context,
        timeRange: TimeRange,
        filter7Days: TextView,
        filter30Days: TextView,
        filter6Months: TextView,
        filterAllTime: TextView
    ) {
        // Reset all filters to unselected state
        val unselectedFilters = listOf(filter7Days, filter30Days, filter6Months, filterAllTime)
        unselectedFilters.forEach { filter ->
            filter.setBackgroundResource(R.drawable.bg_filter_unselected)
            filter.setTextColor(context.getColor(android.R.color.white))
        }
        
        // Set selected filter
        val selectedFilter = when (timeRange) {
            TimeRange.WEEK -> filter7Days
            TimeRange.MONTH -> filter30Days
            TimeRange.SIX_MONTHS -> filter6Months
            TimeRange.ALL_TIME -> filterAllTime
        }
        
        selectedFilter.setBackgroundResource(R.drawable.bg_filter_selected)
        selectedFilter.setTextColor(context.getColor(R.color.accent_red))
    }
}
