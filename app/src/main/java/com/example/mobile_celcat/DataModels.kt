package com.example.mobile_celcat

data class Group(
    val id: String,
    val name: String
)

data class GroupSearchPayload(
    val resourceType: Int = 1001,
    val searchTerm: String?,
    val advancedFilters: List<String> = emptyList(),
    val pagination: Pagination = Pagination(),
    val ordered: Boolean = true,
    val intensive: Boolean = true
)

data class Pagination(
    val pageNum: Int = 0,
    val pageSize: Int = 500
)



data class CalendarEvent(
    val id: String,
    val title: String,
    val location: String,
    val category: String,
    val professor: String,
    val startTime: String,
    val endTime: String,
    val dayOfWeek: Int,
    val notes: String?,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val weeks: List<Boolean> = emptyList(),
    val roomNames: List<String> = emptyList(),
    val professors: List<String> = emptyList(),
    val rawDateStr: String = "" // debug
)

data class ErrorDetail(
    val title: String,
    val targetUrl: String,
    val statusCode: Int? = null,
    val rawMessage: String,
    val stackTrace: String
)